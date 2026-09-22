/*
 * (C) Copyright 2006-2026 Hyland (https://www.hyland.com/).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hyland.openarch.common.utils;

import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Text template processing.
 * <p>
 * Copy files or directories replacing parameters matching pattern '${[a-zA-Z_0-9\-\.]+}' with values from a
 * {@link Properties}.
 * <p>
 * Since 5.7.2, variables can have a default value using syntax ${parameter:=defaultValue}. The default value will be
 * used if parameter is null or unset.
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class TextTemplate {

    private static final Logger log = LogManager.getLogger(TextTemplate.class);

    private static final int MAX_RECURSION_LEVEL = 10;

    private static final String PATTERN_GROUP_VAR = "var";

    private static final String PATTERN_GROUP_DEFAULT = "default";

    /**
     * matches variables of the form "${[#]embeddedVar[:=defaultValue]}" but not those starting with "$${"
     */
    private static final Pattern PATTERN = Pattern.compile("(?<!\\$)\\$\\{" //
            + "(?<" + PATTERN_GROUP_VAR + ">[a-zA-Z_0-9\\-\\.]+)" // embeddedVar
            + "(:=(?<" + PATTERN_GROUP_DEFAULT + ">" // defaultValue
            + "([^{}]*|\\{[^{}]*\\})" // no braces or one balanced set
            + "))?\\}");

    private final Properties vars;

    private boolean trim = false;

    public boolean isTrim() {
        return trim;
    }

    /**
     * Set to true in order to trim invisible characters (spaces) from values.
     */
    public void setTrim(boolean trim) {
        this.trim = trim;
    }

    public TextTemplate() {
        vars = new Properties();
    }

    /**
     * {@link #TextTemplate(Properties)} provides an additional default values behavior
     *
     * @see #TextTemplate(Properties)
     */
    public TextTemplate(Map<String, String> vars) {
        this.vars = new Properties();
        this.vars.putAll(vars);
    }

    /**
     * @param vars Properties containing keys and values for template processing
     */
    public TextTemplate(Properties vars) {
        this.vars = vars;
    }

    public void setVariables(Map<String, String> vars) {
        this.vars.putAll(vars);
    }

    /**
     * If adding multiple variables, prefer use of {@link #setVariables(Map)}
     */
    public void setVariable(String name, String value) {
        vars.setProperty(name, value);
    }

    public String getVariable(String name) {
        return vars.getProperty(name);
    }

    public Properties getVariables() {
        return vars;
    }

    /**
     * That method is not recursive. It processes the given text only once.
     *
     * @param props CryptoProperties containing the variable values
     * @param text Text to process
     * @return the processed text
     * @since 7.4
     */
    protected String processString(Properties props, String text) {
        Matcher m = PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String embeddedVar = m.group(PATTERN_GROUP_VAR);
            String value = props.getProperty(embeddedVar);
            if (value == null) {
                value = m.group(PATTERN_GROUP_DEFAULT);
            }
            if (value != null) {
                if (trim) {
                    value = value.trim();
                }
                // Allow use of backslash and dollars characters
                value = Matcher.quoteReplacement(value);
                m.appendReplacement(sb, value);
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    protected String unescape(String value) {
        // unescape doubled $ characters, only if in front of a {
        return value.replace("$${", "${");
    }

    /**
     * @since 7.4
     */
    public String processText(String text) {
        if (text == null) {
            return null;
        }
        boolean doneProcessing = false;
        int recursionLevel = 0;
        while (!doneProcessing) {
            doneProcessing = true;
            String processedText = processString(vars, text);
            if (!processedText.equals(text)) {
                doneProcessing = false;
                text = processedText;
            }
            recursionLevel++;
            // Avoid infinite replacement loops
            if (!doneProcessing && recursionLevel > MAX_RECURSION_LEVEL) {
                log.warn("Detected potential infinite loop when processing the following text\n" + text);
                break;
            }
        }
        return unescape(text);
    }

}
