/*
 * Copyright (C) 2024 Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.zacsweers.lattice.fir.checkers

import dev.zacsweers.lattice.fir.*
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.resolve.firClassLike

internal object ComponentCreatorChecker : FirClassChecker(MppCheckerKind.Common) {
  override fun check(declaration: FirClass, context: CheckerContext, reporter: DiagnosticReporter) {
    declaration.source ?: return
    val session = context.session
    val latticeClassIds = session.latticeClassIds

    val componentFactoryAnnotation =
      declaration.annotationsIn(session, latticeClassIds.componentFactoryAnnotations).toList()

    if (componentFactoryAnnotation.isEmpty()) return

    declaration.validateFactoryClass(context, reporter, "Component factory") {
      return
    }

    val createFunction =
      declaration.singleAbstractFunction(session, context, reporter, "@Component.Factory") {
        return
      }

    val paramTypes = mutableSetOf<FirTypeKey>()

    for (param in createFunction.valueParameters) {
      val clazz = param.returnTypeRef.firClassLike(session)!!
      val isValid =
        param.isAnnotatedWithAny(session, latticeClassIds.bindsInstanceAnnotations) ||
          clazz.isAnnotatedWithAny(session, latticeClassIds.componentAnnotations)
      if (!isValid) {
        reporter.reportOn(
          param.source,
          FirLatticeErrors.COMPONENT_CREATORS_FACTORY_PARAMS_MUST_BE_BINDSINSTANCE_OR_COMPONENTS,
          context,
        )
        return
      }

      val typeKey = FirTypeKey.from(session, latticeClassIds, param)
      if (!paramTypes.add(typeKey)) {
        reporter.reportOn(
          param.source,
          FirLatticeErrors.COMPONENT_CREATORS_FACTORY_PARAMS_MUST_BE_UNIQUE,
          context,
        )
        return
      }
    }
  }
}
