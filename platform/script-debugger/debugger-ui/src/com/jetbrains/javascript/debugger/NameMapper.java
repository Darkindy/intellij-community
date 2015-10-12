/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.javascript.debugger

import com.google.common.base.CharMatcher
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import gnu.trove.THashMap
import org.jetbrains.debugger.sourcemap.MappingEntry
import org.jetbrains.debugger.sourcemap.MappingList
import org.jetbrains.debugger.sourcemap.SourceMap

import org.jetbrains.rpc.CommandProcessor.LOG

open class NameMapper(private val document: Document, private val generatedDocument: Document, private val sourceMappings: MappingList, private val sourceMap: SourceMap) {
  var rawNameToSource: MutableMap<String, String>? = null
    private set

  // PsiNamedElement, JSVariable for example
  // returns generated name
  fun map(identifierOrNamedElement: PsiElement): String? {
    val offset = identifierOrNamedElement.textOffset
    val line = document.getLineNumber(offset)

    val sourceEntryIndex = sourceMappings.indexOf(line, offset - document.getLineStartOffset(line))
    if (sourceEntryIndex == -1) {
      return null
    }

    val sourceEntry = sourceMappings.get(sourceEntryIndex)
    val next = sourceMappings.getNextOnTheSameLine(sourceEntryIndex, false)
    if (next != null && sourceMappings.getColumn(next) == sourceMappings.getColumn(sourceEntry)) {
      warnSeveralMapping(identifierOrNamedElement)
      return null
    }

    val sourceEntryName = sourceEntry.name
    val generatedName = extractName(getGeneratedName(generatedDocument, sourceMap, sourceEntry))
    if (!generatedName.isEmpty()) {
      var sourceName: String? = sourceEntryName
      if (sourceName == null) {
        sourceName = if (identifierOrNamedElement is PsiNamedElement) identifierOrNamedElement.name else identifierOrNamedElement.text
        if (sourceName == null) {
          return null
        }
      }

      if (rawNameToSource == null) {
        rawNameToSource = THashMap<String, String>()
      }
      rawNameToSource!!.put(generatedName, sourceName)
      return generatedName
    }
    return null
  }

  protected open fun extractName(rawGeneratedName: CharSequence) = NAME_TRIMMER.trimFrom(rawGeneratedName)

  companion object {
    private val S1 = ",()[]{}="
    protected val NAME_TRIMMER = CharMatcher.INVISIBLE.or(CharMatcher.anyOf(S1 + ".&:"))
    // don't trim trailing .&: - could be part of expression
    private val OPERATOR_TRIMMER = CharMatcher.INVISIBLE.or(CharMatcher.anyOf(S1))

    fun warnSeveralMapping(element: PsiElement) {
      // see https://dl.dropboxusercontent.com/u/43511007/s/Screen%20Shot%202015-01-21%20at%2020.33.44.png
      // var1 mapped to the whole "var c, notes, templates, ..." expression text + unrelated text "   ;"
      LOG.warn("incorrect sourcemap, several mappings for named element " + element.text)
    }

    fun trimName(rawGeneratedName: CharSequence, isLastToken: Boolean): String {
      return (if (isLastToken) NAME_TRIMMER else OPERATOR_TRIMMER).trimFrom(rawGeneratedName)
    }
  }
}

private fun getGeneratedName(document: Document, sourceMap: SourceMap, sourceEntry: MappingEntry): CharSequence {
  val lineStartOffset = document.getLineStartOffset(sourceEntry.generatedLine)
  val nextGeneratedMapping = sourceMap.mappings.getNextOnTheSameLine(sourceEntry)
  val endOffset: Int
  if (nextGeneratedMapping == null) {
    endOffset = document.getLineEndOffset(sourceEntry.generatedLine)
  }
  else {
    endOffset = lineStartOffset + nextGeneratedMapping.generatedColumn
  }
  return document.immutableCharSequence.subSequence(lineStartOffset + sourceEntry.generatedColumn, endOffset)
}