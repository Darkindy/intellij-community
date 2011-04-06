package com.jetbrains.python.documentation;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class EpydocString extends StructuredDocString {
  public EpydocString(String docstringText) {
    super(docstringText, "@");
  }

  @Override
  @Nullable
  public String getReturnType() {
    String value = getTagValue("rtype");
    return removeInlineMarkup(value);
  }

  @Override
  @Nullable
  public String getParamType(String paramName) {
    String value = getTagValue("type", paramName);
    return removeInlineMarkup(value);
  }

  @Override
  @Nullable
  public String getParamDescription(String paramName) {
    String value = getTagValue("param", paramName);
    if (value == null) {
      value = getTagValue("param", "*" + paramName);
    }
    if (value == null) {
      value = getTagValue("param", "**" + paramName);
    }
    return inlineMarkupToHTML(value);
  }

  @Nullable
  public static String removeInlineMarkup(String s) {
    return convertInlineMarkup(s, false);
  }

  @Nullable
  private static String convertInlineMarkup(String s, boolean toHTML) {
    if (s == null) return null;
    StringBuilder resultBuilder = new StringBuilder();
    appendWithMarkup(s, resultBuilder, toHTML);
    return resultBuilder.toString();
  }

  private static void appendWithMarkup(String s, StringBuilder resultBuilder, boolean toHTML) {
    int pos = 0;
    while(true) {
      int bracePos = s.indexOf('{', pos);
      if (bracePos < 1) break;
      char prevChar = s.charAt(bracePos-1);
      if (prevChar >= 'A' && prevChar <= 'Z') {
        resultBuilder.append(s.substring(pos, bracePos - 1));
        int rbracePos = findMatchingEndBrace(s, bracePos);
        if (rbracePos < 0) {
          pos = bracePos + 1;
          break;
        }
        final String inlineMarkupContent = s.substring(bracePos + 1, rbracePos);
        if (toHTML) {
          appendInlineMarkup(resultBuilder, prevChar, inlineMarkupContent);
        }
        else {
          resultBuilder.append(inlineMarkupContent);
        }
        pos = rbracePos + 1;
      }
      else {
        resultBuilder.append(StringUtil.escapeXml(joinLines(s.substring(pos, bracePos + 1), true)));
        pos = bracePos+1;
      }
    }
    resultBuilder.append(StringUtil.escapeXml(joinLines(s.substring(pos), true)));
  }

  private static int findMatchingEndBrace(String s, int bracePos) {
    int braceCount = 1;
    for(int pos=bracePos+1; pos < s.length(); pos++) {
      char c = s.charAt(pos);
      if (c == '{') braceCount++;
      else if (c == '}') {
        braceCount--;
        if (braceCount == 0) return pos;
      }
    }
    return -1;
  }

  private static void appendInlineMarkup(StringBuilder resultBuilder, char markupChar, String markupContent) {
    if (markupChar == 'U') {
      appendLink(resultBuilder, markupContent);
      return;
    }
    switch (markupChar) {
      case 'I':
        appendTagPair(resultBuilder, markupContent, "i");
        break;
      case 'B':
        appendTagPair(resultBuilder, markupContent, "b");
        break;
      case 'C':
        appendTagPair(resultBuilder, markupContent, "pre");
        break;
      default:
        resultBuilder.append(StringUtil.escapeXml(markupContent));
        break;
    }
  }

  private static void appendTagPair(StringBuilder resultBuilder, String markupContent, final String tagName) {
    resultBuilder.append("<").append(tagName).append(">");
    appendWithMarkup(markupContent, resultBuilder, true);
    resultBuilder.append("</").append(tagName).append(">");
  }

  private static void appendLink(StringBuilder resultBuilder, String markupContent) {
    String linkText = StringUtil.escapeXml(markupContent);
    String linkUrl = linkText;
    int pos = markupContent.indexOf('<');
    if (pos >= 0 && markupContent.endsWith(">")) {
      linkText = StringUtil.escapeXml(markupContent.substring(0, pos).trim());
      linkUrl = StringUtil.escapeXml(joinLines(markupContent.substring(pos + 1, markupContent.length() - 1), false));
    }
    resultBuilder.append("<a href=\"");
    if (!linkUrl.matches("[a-z]+:.+")) {
      resultBuilder.append("http://");
    }
    resultBuilder.append(linkUrl).append("\">").append(linkText).append("</a>");
  }

  private static String joinLines(String s, boolean addSpace) {
    while(true) {
      int lineBreakStart = s.indexOf('\n');
      if (lineBreakStart < 0) break;
      int lineBreakEnd = lineBreakStart+1;
      while(lineBreakEnd < s.length() && s.charAt(lineBreakEnd) == ' ') {
        lineBreakEnd++;
      }
      s = s.substring(0, lineBreakStart) + (addSpace ? " " : "") + s.substring(lineBreakEnd);
    }
    return s;
  }

  @Nullable
  public static String inlineMarkupToHTML(String s) {
    return convertInlineMarkup(s, true);
  }
}
