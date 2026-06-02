/*
 * (C) Copyright 2024 Hyland (https://hyland.com/) and others.
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
 *
 * Contributors:
 *     Florent Guillaume
 */
lexer grammar ExpressionLexer;

@header {
/*
 * THIS FILE IS AUTO-GENERATED, DO NOT EDIT.
 *
 * (C) Copyright 2024 Hyland (https://hyland.com/) and others.
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
 *
 * Contributors:
 *     Florent Guillaume
 *
 * THIS FILE IS AUTO-GENERATED, DO NOT EDIT.
 */
}

WS: [ \t\r\n]+ -> channel(HIDDEN);

EQ:     '==';
NOTEQ:  '!=';
LT:     '<';
GT:     '>';
LTEQ:   '<=';
GTEQ:   '>=';
BANG:   '!';
AND:    '&&';
OR:     '||';

PLUS:   '+';
MINUS:  '-';
STAR:   '*';
DIV:    '/';

QMARK:  '?';
COLON:  ':';

LPAREN: '(';
RPAREN: ')';
COMMA:  ',';
DOT:    '.';

fragment OptSign: [+-]?;
fragment Digits: [0-9]+;
NUMBER: OptSign Digits (DOT Digits)? ([eE] OptSign Digits)?;

IDENTIFIER: [a-zA-Z_] [a-zA-Z_0-9]*;

STRING: '"' ('\\' [\\"tnr] | ~[\\"])* '"';
