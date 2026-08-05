/*
 * (C) Copyright 2024-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.security.expr;

import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.nuxeo.ecm.core.security.expr.ExpressionParser.ExpressionAddSubContext;
import org.nuxeo.ecm.core.security.expr.ExpressionParser.ExpressionAndContext;
import org.nuxeo.ecm.core.security.expr.ExpressionParser.ExpressionContext;
import org.nuxeo.ecm.core.security.expr.ExpressionParser.ExpressionMulDivContext;
import org.nuxeo.ecm.core.security.expr.ExpressionParser.ExpressionNotContext;
import org.nuxeo.ecm.core.security.expr.ExpressionParser.ExpressionOperandContext;
import org.nuxeo.ecm.core.security.expr.ExpressionParser.ExpressionOrContext;
import org.nuxeo.ecm.core.security.expr.ExpressionParser.ExpressionParenContext;
import org.nuxeo.ecm.core.security.expr.ExpressionParser.ExpressionRelOpContext;
import org.nuxeo.ecm.core.security.expr.ExpressionParser.ExpressionTernaryContext;
import org.nuxeo.ecm.core.security.expr.ExpressionParser.LiteralNumberContext;
import org.nuxeo.ecm.core.security.expr.ExpressionParser.LiteralStringContext;
import org.nuxeo.ecm.core.security.expr.ExpressionParser.OperandLiteralContext;
import org.nuxeo.ecm.core.security.expr.ExpressionParser.OperandValueContext;
import org.nuxeo.ecm.core.security.expr.ExpressionParser.ParseContext;
import org.nuxeo.ecm.core.security.expr.ExpressionParser.SimpleIdentifierContext;
import org.nuxeo.ecm.core.security.expr.ExpressionParser.SimpleMethodCallContext;
import org.nuxeo.ecm.core.security.expr.ExpressionParser.ValueContext;
import org.nuxeo.ecm.core.security.expr.ast.Expr;
import org.nuxeo.ecm.core.security.expr.ast.ExprBin;
import org.nuxeo.ecm.core.security.expr.ast.ExprTern;
import org.nuxeo.ecm.core.security.expr.ast.ExprUn;
import org.nuxeo.ecm.core.security.expr.ast.Lit;
import org.nuxeo.ecm.core.security.expr.ast.LitBool;
import org.nuxeo.ecm.core.security.expr.ast.LitNull;
import org.nuxeo.ecm.core.security.expr.ast.LitNum;
import org.nuxeo.ecm.core.security.expr.ast.LitStr;
import org.nuxeo.ecm.core.security.expr.ast.Op;
import org.nuxeo.ecm.core.security.expr.ast.Val;
import org.nuxeo.ecm.core.security.expr.ast.ValCall;
import org.nuxeo.ecm.core.security.expr.ast.ValDot;
import org.nuxeo.ecm.core.security.expr.ast.ValId;
import org.nuxeo.ecm.core.security.expr.ast.ValSimple;

public class ExpressionVisitor extends ExpressionParserBaseVisitor<Expr> {

    private static class ErrorListener extends BaseErrorListener {

        public static final ErrorListener INSTANCE = new ErrorListener();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
                String msg, RecognitionException e) throws ParseCancellationException {
            throw new ParseCancellationException("At position " + line + ":" + charPositionInLine + " " + msg);
        }
    }

    public Expr parse(String string) throws IllegalArgumentException {
        try {
            ExpressionLexer lexer = new ExpressionLexer(CharStreams.fromString(string));
            lexer.removeErrorListeners();
            lexer.addErrorListener(ErrorListener.INSTANCE);
            ExpressionParser parser = new ExpressionParser(new CommonTokenStream(lexer));
            parser.removeErrorListeners();
            parser.addErrorListener(ErrorListener.INSTANCE);
            return visit(parser.parse());
        } catch (ParseCancellationException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private String parseString(Token token) {
        try {
            return parseString(token.getText());
        } catch (IllegalArgumentException e) {
            ErrorListener.INSTANCE.syntaxError(null, token, 0, 0, "Invalid string", null);
            return null; // NOT REACHED
        }
    }

    // ad hoc parser rather than letting the lexer do complex stuff
    private static String parseString(String raw) {
        int len = raw.length();
        if (len < 2 || raw.charAt(0) != '"' || raw.charAt(len - 1) != '"') {
            // impossible in lexer
            throw new IllegalArgumentException();
        }
        if (!raw.contains("\\")) {
            // no escapes, return substring directly
            return raw.substring(1, len - 1);
        }
        StringBuilder s = new StringBuilder(len - 3); // approx length
        for (int i = 1; i < len - 1; i++) {
            char c = raw.charAt(i);
            if (c == '\\') {
                if (i == len - 2) {
                    // backslash in last position (impossible in lexer)
                    throw new IllegalArgumentException();
                }
                c = raw.charAt(++i);
                c = switch (c) {
                case '\\', '"' -> c;
                case 't' -> '\t';
                case 'n' -> '\n';
                case 'r' -> '\r';
                default -> throw new IllegalArgumentException(); // impossible in lexer
                };
            }
            s.append(c);
        }
        return s.toString();
    }

    private Number parseNumber(Token token) {
        try {
            return parseNumber(token.getText());
        } catch (NumberFormatException e) {
            ErrorListener.INSTANCE.syntaxError(null, token, 0, 0, "Invalid number", null);
            return null; // NOT REACHED
        }
    }

    private static Number parseNumber(String string) {
        if (string.contains(".") || string.contains("e") || string.contains("E")) {
            Double d = Double.valueOf(string);
            if (d.isInfinite()) {
                throw new NumberFormatException(string);
            }
            return d;
        } else {
            return Long.valueOf(string);
        }
    }

    @Override
    public Expr visitParse(ParseContext ctx) {
        return ctx.e.accept(this);
    }

    @Override
    public Expr visitExpressionOperand(ExpressionOperandContext ctx) {
        return ctx.o.accept(this);
    }

    @Override
    public Expr visitExpressionMulDiv(ExpressionMulDivContext ctx) {
        return visitExpressionBinary(ctx.l, ctx.op, ctx.r);
    }

    @Override
    public Expr visitExpressionAddSub(ExpressionAddSubContext ctx) {
        return visitExpressionBinary(ctx.l, ctx.op, ctx.r);
    }

    @Override
    public Expr visitExpressionRelOp(ExpressionRelOpContext ctx) {
        return visitExpressionBinary(ctx.l, ctx.op.op, ctx.r);
    }

    @Override
    public Expr visitExpressionAnd(ExpressionAndContext ctx) {
        return visitExpressionBinary(ctx.l, ctx.op, ctx.r);
    }

    @Override
    public Expr visitExpressionOr(ExpressionOrContext ctx) {
        return visitExpressionBinary(ctx.l, ctx.op, ctx.r);
    }

    private Expr visitExpressionBinary(ExpressionContext l, Token optok, ExpressionContext r) {
        var left = l.accept(this);
        var right = r.accept(this);
        Op op = switch (optok.getType()) {
        case ExpressionParser.STAR -> Op.MUL;
        case ExpressionParser.DIV -> Op.DIV;
        case ExpressionParser.PLUS -> Op.ADD;
        case ExpressionParser.MINUS -> Op.SUB;
        case ExpressionParser.EQ -> Op.EQ;
        case ExpressionParser.NOTEQ -> Op.NOTEQ;
        case ExpressionParser.LT -> Op.LT;
        case ExpressionParser.GT -> Op.GT;
        case ExpressionParser.LTEQ -> Op.LTEQ;
        case ExpressionParser.GTEQ -> Op.GTEQ;
        case ExpressionParser.AND -> Op.AND;
        case ExpressionParser.OR -> Op.OR;
        default -> throw new IllegalStateException("Unexpected case: " + optok.getText());
        };
        return new ExprBin(left, op, right);
    }

    @Override
    public Expr visitExpressionTernary(ExpressionTernaryContext ctx) {
        var pred = ctx.e.accept(this);
        var iftrue = ctx.t.accept(this);
        var iffalse = ctx.f.accept(this);
        return new ExprTern(pred, iftrue, iffalse);
    }

    @Override
    public Expr visitExpressionParen(ExpressionParenContext ctx) {
        return ctx.e.accept(this);
    }

    @Override
    public Expr visitExpressionNot(ExpressionNotContext ctx) {
        var expr = ctx.e.accept(this);
        return new ExprUn(Op.NOT, expr);
    }

    @Override
    public Expr visitOperandLiteral(OperandLiteralContext ctx) {
        return ctx.v.accept(this);
    }

    @Override
    public Expr visitOperandValue(OperandValueContext ctx) {
        return ctx.v.accept(this);
    }

    @Override
    public Expr visitLiteralString(LiteralStringContext ctx) {
        return new LitStr(parseString(ctx.v));
    }

    @Override
    public Expr visitLiteralNumber(LiteralNumberContext ctx) {
        return new LitNum(parseNumber(ctx.v));
    }

    @Override
    public Expr visitValue(ValueContext ctx) {
        var list = ctx.simple().stream().map(c -> c.accept(this)).toList();
        Expr first = list.get(0);
        if (!(first instanceof Val current)) {
            if (list.size() == 1) {
                return first;
            }
            ErrorListener.INSTANCE.syntaxError(null, ctx.simple(0), 0, 0, "Invalid identifier", null);
            return null; // NOT REACHED
        }
        for (int i = 1; i < list.size(); i++) {
            Expr expr = list.get(i);
            if (!(expr instanceof ValSimple simple)) {
                ErrorListener.INSTANCE.syntaxError(null, ctx.simple(i), 0, 0, "Invalid identifier", null);
                return null; // NOT REACHED
            }
            current = new ValDot(current, simple);
        }
        return current;
    }

    @Override
    public Expr visitSimpleIdentifier(SimpleIdentifierContext ctx) {
        Token nameToken = ctx.name;
        String name = nameToken.getText();
        Lit lit = getLitBoolOrNull(name);
        if (lit != null) {
            return lit;
        }
        return new ValId(name);
    }

    @Override
    public Expr visitSimpleMethodCall(SimpleMethodCallContext ctx) {
        Token nameToken = ctx.name;
        String name = nameToken.getText();
        Lit lit = getLitBoolOrNull(name);
        if (lit != null) {
            ErrorListener.INSTANCE.syntaxError(null, nameToken, 0, 0, "Invalid identifier", null);
            return null; // NOT REACHED
        }
        List<Expr> list = ctx.args == null ? List.of()
                : ctx.args.expression().stream().map(c -> c.accept(this)).toList();
        return new ValCall(name, list);
    }

    private Lit getLitBoolOrNull(String name) {
        return switch (name) {
        case "null", "NULL" -> new LitNull();
        case "true", "TRUE" -> new LitBool(true);
        case "false", "FALSE" -> new LitBool(false);
        default -> null;
        };
    }

}
