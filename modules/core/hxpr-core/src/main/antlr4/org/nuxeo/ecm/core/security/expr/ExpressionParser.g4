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
parser grammar ExpressionParser;

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
    tokenVocab = ExpressionLexer;
}

parse: e=expression EOF;

// the order below is important, it determines operator precedence
expression: o=operand                                               # expressionOperand
          | BANG e=expression                                       # expressionNot
          | l=expression op=(STAR | DIV) r=expression               # expressionMulDiv
          | l=expression op=(PLUS | MINUS) r=expression             # expressionAddSub
          | l=expression op=relop r=expression                      # expressionRelOp
          | l=expression op=AND r=expression                        # expressionAnd
          | l=expression op=OR r=expression                         # expressionOr
          | e=expression QMARK t=expression COLON f=expression      # expressionTernary
          | LPAREN e=expression RPAREN                              # expressionParen
          ;

relop: op=(EQ | NOTEQ | LT | GT | LTEQ | GTEQ);

operand: v=literal                                                  # operandLiteral
       | v=value                                                    # operandValue
       ;

literal: v=STRING                                                   # literalString
       | v=NUMBER                                                   # literalNumber
       ;

value: simple (DOT simple)*;

simple: name=IDENTIFIER                                             # simpleIdentifier
      | name=IDENTIFIER LPAREN args=expression_list? RPAREN         # simpleMethodCall
      ;

expression_list: expression (COMMA expression)*;
