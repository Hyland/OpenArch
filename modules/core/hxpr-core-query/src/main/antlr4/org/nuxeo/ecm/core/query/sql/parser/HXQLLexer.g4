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
lexer grammar HXQLLexer;

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

options {
    caseInsensitive = true;
}

WS: [ \t\r\n]+ -> channel(HIDDEN);

SELECT:     'SELECT';
FROM:       'FROM';
WHERE:      'WHERE';
NOT:        'NOT';
AND:        'AND';
OR:         'OR';
IS:         'IS';
NULL:       'NULL';
LIKE:       'LIKE';
ILIKE:      'ILIKE';
BETWEEN:    'BETWEEN';
IN:         'IN';
ORDERBY:    'ORDER BY';
ASC:        'ASC';
DESC:       'DESC';
DATE:       'DATE';
TIMESTAMP:  'TIMESTAMP';
LIMIT:      'LIMIT';
OFFSET:     'OFFSET';
STARTSWITH: 'STARTSWITH';
TRUE:       'TRUE';
FALSE:      'FALSE';

MUL:    '*';   // only as the SELECT * projection, there is no arithmetic in HXQL

EQ:     '=';
NOTEQ:  '<>';
NOTEQC: '!=';
LT:     '<';
GT:     '>';
LTEQ:   '<=';
GTEQ:   '>=';

LPAR:   '(';
RPAR:  ')';
COMMA:  ',';

fragment OptSign: ('+'|'-')?;
fragment Digits:  ('0'..'9')+;
fragment Decimal: Digits '.' Digits | Digits '.' | '.' Digits;
INTEGER: OptSign Digits;
DOUBLE:  OptSign (Decimal | (Digits|Decimal) 'E' OptSign Digits);

// full text
LHINT_FT: '/*+FT:';
RHINT:    '*/';
INDEX:    'INDEX';
ANALYZER: 'ANALYZER';
OPERATOR: 'OPERATOR';

// note: because we're case insensitive we don't need to say [a-zA-Z]
fragment Identifier: [a-z$_] [a-z0-9$_:.-]*;
fragment IdentifierOrIndex: Identifier | Index | Identifier '[' Index ']';
fragment Index: Digits | '*' | '*' Digits;
IDENTIFIER:      Identifier;
PARAM:           ':' Identifier;   // named query parameter, bound before execution
FROM_IDENTIFIER: Identifier ('.' Identifier)*;
PATH_IDENTIFIER: Identifier ('/' IdentifierOrIndex)+;
FT_IDENTIFIER: [a-z] [a-z0-9_:.^-]*;                  // allow boost with ^

STRING:     '"'  ('\\' ["\\tnr] | ~["\\\n\r])* '"';
SQ_STRING:  '\'' ('\\' ['\\tnr] | ~['\\\n\r])* '\'';
