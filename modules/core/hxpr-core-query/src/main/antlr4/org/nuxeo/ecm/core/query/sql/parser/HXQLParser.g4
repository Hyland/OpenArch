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
parser grammar HXQLParser;

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
    tokenVocab = HXQLLexer;
}


parse: q=query EOF;

query: select=select_clause from=from_clause
           where=where_clause?
           orderBy=order_by_clause?
	       (LIMIT limit=INTEGER (OFFSET offset=INTEGER)?)?;

select_clause: SELECT list=select_list;

select_list: MUL
           | select_elem (COMMA select_elem)*
           ;

select_elem: o=operand (AS alias=IDENTIFIER)?;

from_clause: FROM list=from_list;

from_list: from_elem (COMMA from_elem)*;

from_elem: (id=IDENTIFIER | id=FROM_IDENTIFIER) (AS alias=IDENTIFIER)?;

where_clause: WHERE e=predicate;

order_by_clause: ORDERBY list=order_by_list;

order_by_list: order_by_expr (COMMA order_by_expr)*;

order_by_expr: ref=reference (ASC | DESC)?;

predicate: l=expression op=(EQ | NOTEQ | NOTEQC | LT | GT | LTEQ | GTEQ) r=expression   # predicateCompare
         | ref=reference NOT? IN LPAR list=literal_list RPAR        # predicateIn
         | ref=reference NOT? BETWEEN min=literal AND max=literal   # predicateBetween
         | ref=reference NOT? (LIKE | ILIKE) s=string               # predicateLike
         | ref=reference IS NOT? NULL                               # predicateNull
         | ref=reference STARTSWITH s=string                        # predicateStartsWith
         | LPAR p=predicate RPAR                                    # predicateParen
         | NOT p=predicate                                          # predicateNot
         | l=predicate AND r=predicate                              # predicateAnd
         | l=predicate OR r=predicate                               # predicateOr
         ;

expression: o=operand                                               # expressionOperand
          | LPAR e=expression RPAR                                  # expressionParen
          | l=expression op=(MUL | DIV) r=expression                # expressionMulDiv
          | l=expression op=(SUM | SUB) r=expression                # expressionSumSub
          ;

reference: ref=simple_reference                                     # referenceSimple
         | DATE LPAR ref=simple_reference RPAR                      # referenceDate
         | hint=ft_hint ref=simple_reference                        # referenceHint
         ;

simple_reference: v=(IDENTIFIER | PATH_IDENTIFIER);

operand: v=literal
       | r=reference
       | f=function
       ;

literal: v=string                                                   # literalString
       | v=(TRUE | FALSE)                                           # literalBoolean
       | v=INTEGER                                                  # literalInteger
       | v=DOUBLE                                                   # literalDouble
       | DATE v=string                                              # literalDate
       | TIMESTAMP v=string                                         # literalTimestamp
       ;

string: s=(STRING | SQ_STRING);

literal_list: literal (COMMA literal)*;

function: name=IDENTIFIER LPAR args=function_args? RPAR;

function_args: expression (COMMA expression)*;

ft_hint: LHINT_FT index=ft_index? analyzer=ft_analyzer? operator=ft_operator? RHINT;

ft_index:    INDEX    LPAR ft_identifier_list RPAR;

ft_analyzer: ANALYZER LPAR id=ft_identifier RPAR;

ft_operator: OPERATOR LPAR id=ft_identifier RPAR;

ft_identifier_list: ft_identifier (COMMA ft_identifier)*;

ft_identifier: id=(IDENTIFIER | FT_IDENTIFIER);
