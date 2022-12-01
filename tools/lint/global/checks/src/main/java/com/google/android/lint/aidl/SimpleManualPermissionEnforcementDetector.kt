/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.lint.aidl

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.google.android.lint.findCallExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.skipParenthesizedExprDown

/**
 * Looks for methods implementing generated AIDL interface stubs
 * that can have simple permission checks migrated to
 * @EnforcePermission annotations
 *
 * TODO: b/242564870 (enable parse and autoFix of .aidl files)
 */
@Suppress("UnstableApiUsage")
class SimpleManualPermissionEnforcementDetector : AidlImplementationDetector() {
    override fun visitAidlMethod(
            context: JavaContext,
            node: UMethod,
            interfaceName: String,
            body: UBlockExpression
    ) {
        val enforcePermissionFix = accumulateSimplePermissionCheckFixes(body, context) ?: return
        val lintFix = enforcePermissionFix.toLintFix(context.getLocation(node))
        val message =
                "$interfaceName permission check ${
                    if (enforcePermissionFix.errorLevel) "should" else "can"
                } be converted to @EnforcePermission annotation"

        val incident = Incident(
                ISSUE_SIMPLE_MANUAL_PERMISSION_ENFORCEMENT,
                enforcePermissionFix.locations.last(),
                message,
                lintFix
        )

        if (enforcePermissionFix.errorLevel) {
            incident.overrideSeverity(Severity.ERROR)
        }

        context.report(incident)
    }

    /**
     * Walk the expressions in the method, looking for simple permission checks.
     *
     * If a single permission check is found at the beginning of the method,
     * this should be migrated to @EnforcePermission(value).
     *
     * If multiple consecutive permission checks are found,
     * these should be migrated to @EnforcePermission(allOf={value1, value2, ...})
     *
     * As soon as something other than a permission check is encountered, stop looking,
     * as some other business logic is happening that prevents an automated fix.
     */
    private fun accumulateSimplePermissionCheckFixes(
                methodBody: UBlockExpression,
                context: JavaContext
        ): EnforcePermissionFix? {
        try {
            val singleFixes = mutableListOf<EnforcePermissionFix>()
            for (expression in methodBody.expressions) {
                val fix = getPermissionCheckFix(
                        expression.skipParenthesizedExprDown(),
                        context) ?: break
                singleFixes.add(fix)
            }
            return when (singleFixes.size) {
                0 -> null
                1 -> singleFixes[0]
                else -> EnforcePermissionFix.compose(singleFixes)
            }
        } catch (e: AnyOfAllOfException) {
            return null
        }
    }


    /**
     * If an expression boils down to a permission check, return
     * the helper for creating a lint auto fix to @EnforcePermission
     */
    private fun getPermissionCheckFix(startingExpression: UElement?, context: JavaContext):
            EnforcePermissionFix? {
        if (startingExpression is UIfExpression) {
            return EnforcePermissionFix.fromIfExpression(context, startingExpression)
        }
        findCallExpression(startingExpression)?.let {
            return EnforcePermissionFix.fromCallExpression(context, it)
        }
        return null
    }

    companion object {

        private val EXPLANATION = """
            Whenever possible, method implementations of AIDL interfaces should use the @EnforcePermission
            annotation to declare the permissions to be enforced.  The verification code is then
            generated by the AIDL compiler, which also takes care of annotating the generated java
            code.

            This reduces the risk of bugs around these permission checks (that often become vulnerabilities).
            It also enables easier auditing and review.

            Please migrate to an @EnforcePermission annotation. (See: go/aidl-enforce-howto)
        """.trimIndent()

        @JvmField
        val ISSUE_SIMPLE_MANUAL_PERMISSION_ENFORCEMENT = Issue.create(
                id = "SimpleManualPermissionEnforcement",
                briefDescription = "Manual permission check can be @EnforcePermission annotation",
                explanation = EXPLANATION,
                category = Category.SECURITY,
                priority = 5,
                severity = Severity.WARNING,
                implementation = Implementation(
                        SimpleManualPermissionEnforcementDetector::class.java,
                        Scope.JAVA_FILE_SCOPE
                ),
                enabledByDefault = false, // TODO: enable once b/241171714 is resolved
        )
    }
}
