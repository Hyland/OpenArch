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
package org.nuxeo.ecm.core.query.sql.parser;

import static java.util.stream.Collectors.toMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.model.ASTNode;
import org.nuxeo.ecm.core.query.sql.model.BooleanLiteral;
import org.nuxeo.ecm.core.query.sql.model.DateLiteral;
import org.nuxeo.ecm.core.query.sql.model.DoubleLiteral;
import org.nuxeo.ecm.core.query.sql.model.Expression;
import org.nuxeo.ecm.core.query.sql.model.FromClause;
import org.nuxeo.ecm.core.query.sql.model.FromList;
import org.nuxeo.ecm.core.query.sql.model.FtHint;
import org.nuxeo.ecm.core.query.sql.model.FtIdentifierList;
import org.nuxeo.ecm.core.query.sql.model.IntegerLiteral;
import org.nuxeo.ecm.core.query.sql.model.Literal;
import org.nuxeo.ecm.core.query.sql.model.LiteralList;
import org.nuxeo.ecm.core.query.sql.model.Operand;
import org.nuxeo.ecm.core.query.sql.model.OperandList;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.OrderByClause;
import org.nuxeo.ecm.core.query.sql.model.OrderByExpr;
import org.nuxeo.ecm.core.query.sql.model.OrderByList;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.SQLQuery;
import org.nuxeo.ecm.core.query.sql.model.SelectClause;
import org.nuxeo.ecm.core.query.sql.model.SelectList;
import org.nuxeo.ecm.core.query.sql.model.StringLiteral;
import org.nuxeo.ecm.core.query.sql.model.WhereClause;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.ExpressionContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.ExpressionMulDivContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.ExpressionOperandContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.ExpressionParenContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.ExpressionSumSubContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.From_clauseContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.From_listContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.Ft_hintContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.Ft_identifier_listContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.Ft_indexContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.FunctionContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.Function_argsContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.LiteralBooleanContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.LiteralDateContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.LiteralDoubleContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.LiteralIntegerContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.LiteralStringContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.LiteralTimestampContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.Literal_listContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.OperandContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.Order_by_clauseContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.Order_by_exprContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.Order_by_listContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.ParseContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.PredicateAndContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.PredicateBetweenContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.PredicateCompareContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.PredicateContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.PredicateInContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.PredicateLikeContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.PredicateNotContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.PredicateNullContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.PredicateOrContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.PredicateParenContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.PredicateStartsWithContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.QueryContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.ReferenceDateContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.ReferenceHintContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.ReferenceSimpleContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.Select_clauseContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.Select_listContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.Simple_referenceContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.StringContext;
import org.nuxeo.ecm.core.query.sql.parser.HXQLParser.Where_clauseContext;

public class HXQLVisitor extends HXQLParserBaseVisitor<ASTNode> {

    private static class ErrorListener extends BaseErrorListener {

        public static final ErrorListener INSTANCE = new ErrorListener();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
                String msg, RecognitionException e) throws ParseCancellationException {
            throw new ParseCancellationException("At position " + line + ":" + charPositionInLine + " " + msg);
        }
    }

    public SQLQuery parse(String string) throws QueryParseException {
        try {
            HXQLLexer lexer = new HXQLLexer(CharStreams.fromString(string));
            lexer.removeErrorListeners();
            lexer.addErrorListener(ErrorListener.INSTANCE);
            HXQLParser parser = new HXQLParser(new CommonTokenStream(lexer));
            parser.removeErrorListeners();
            parser.addErrorListener(ErrorListener.INSTANCE);
            SQLQuery query = (SQLQuery) visit(parser.parse());
            query.setQueryString(string);
            return query;
        } catch (ParseCancellationException e) {
            throw new QueryParseException(e.getMessage(), e);
        }
    }

    private static long parseLongOrZero(Token token) {
        return token == null ? 0 : Long.parseLong(token.getText());
    }

    private String parseString(Token token) {
        try {
            return parseString(token.getText());
        } catch (IllegalArgumentException e) {
            ErrorListener.INSTANCE.syntaxError(null, token, 0, 0, "Invalid string" , null);
            return null; // NOT REACHED
        }
    }

    // ad hoc parser rather than letting the lexer do complex stuff
    // protected for tests
    protected static String parseString(String raw) {
        int len = raw.length();
        if (len < 2) {
            throw new IllegalArgumentException();
        }
        char quote = raw.charAt(0);
        if (quote != '"' && quote != '\'') {
            throw new IllegalArgumentException();
        }
        if (quote != raw.charAt(len - 1)) {
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
                    // backslash in last position (although not possible in lexer)
                    throw new IllegalArgumentException();
                }
                c = raw.charAt(++i);
                c = switch (c) {
                case '\\', '"', '\'' -> c;
                case 't' -> '\t';
                case 'n' -> '\n';
                case 'r' -> '\r';
                default -> throw new IllegalArgumentException();
                };
            }
            s.append(c);
        }
        return s.toString();
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> acceptList(List<? extends RuleContext> list) {
        return list.stream().map(ctx -> (T) ctx.accept(this)).toList();
    }

    public static <T, K, U> Collector<T, ?, Map<K, U>> toLinkedMap(Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends U> valueMapper) {
        return toMap(keyMapper, valueMapper, (u, v) -> v, LinkedHashMap::new);
    }

    @Override
    public ASTNode visitParse(ParseContext ctx) {
        return ctx.q.accept(this);
    }

    @Override
    public ASTNode visitQuery(QueryContext ctx) {
        var select = (SelectClause) ctx.select.accept(this);
        var from = (FromClause) ctx.from.accept(this);
        var where = ctx.where == null ? null : (WhereClause) ctx.where.accept(this);
        var orderBy = ctx.orderBy == null ? null : (OrderByClause) ctx.orderBy.accept(this);
        long limit = parseLongOrZero(ctx.limit);
        long offset = parseLongOrZero(ctx.offset);
        return new SQLQuery(select, from, where, null, null, orderBy, limit, offset);
    }

    @Override
    public ASTNode visitSelect_clause(Select_clauseContext ctx) {
        var list = (SelectList) ctx.list.accept(this);
        return new SelectClause(list);
    }

    @Override
    public ASTNode visitSelect_list(Select_listContext ctx) {
        if (ctx.MUL() != null) {
            return new SelectList();
        }
        var map = ctx.select_elem()
                     .stream()
                     .collect(toLinkedMap( //
                             c -> c.alias != null ? c.alias.getText() : c.o.getText(), //
                             c -> (Operand) c.o.accept(this)));
        return new SelectList(map);
    }

    @Override
    public ASTNode visitFrom_clause(From_clauseContext ctx) {
        var elements = (FromList) ctx.list.accept(this);
        return new FromClause(elements);
    }

    @Override
    public ASTNode visitFrom_list(From_listContext ctx) {
        var map = ctx.from_elem()
                     .stream()
                     .collect(toLinkedMap( //
                             c -> c.alias != null ? c.alias.getText() : c.id.getText(), //
                             c -> c.id.getText()));
        return new FromList(map);
    }

    @Override
    public ASTNode visitWhere_clause(Where_clauseContext ctx) {
        var predicate = (Predicate) ctx.e.accept(this);
        return new WhereClause(predicate);
    }

    @Override
    public ASTNode visitOrder_by_clause(Order_by_clauseContext ctx) {
        var orderByList = (OrderByList) ctx.list.accept(this);
        return new OrderByClause(orderByList);
    }

    @Override
    public ASTNode visitOrder_by_list(Order_by_listContext ctx) {
        List<OrderByExpr> list = acceptList(ctx.order_by_expr());
        return new OrderByList(list);
    }

    @Override
    public ASTNode visitOrder_by_expr(Order_by_exprContext ctx) {
        var ref = (Reference) ctx.ref.accept(this);
        boolean isDescending = ctx.DESC() != null;
        return new OrderByExpr(ref, isDescending);
    }

    @Override
    public ASTNode visitPredicateCompare(PredicateCompareContext ctx) {
        var left = (Operand) ctx.l.accept(this);
        var right = (Operand) ctx.r.accept(this);
        Operator op = switch (ctx.op.getType()) {
        case HXQLParser.EQ -> Operator.EQ;
        case HXQLParser.NOTEQ -> Operator.NOTEQ;
        case HXQLParser.NOTEQC -> Operator.NOTEQ;
        case HXQLParser.LT -> Operator.LT;
        case HXQLParser.GT -> Operator.GT;
        case HXQLParser.LTEQ -> Operator.LTEQ;
        case HXQLParser.GTEQ -> Operator.GTEQ;
        default -> throw new IllegalStateException("Unexpected case: " + ctx.op);
        };
        return new Predicate(left, op, right);
    }

    @Override
    public ASTNode visitPredicateIn(PredicateInContext ctx) {
        var ref = (Operand) ctx.ref.accept(this);
        var list = (Operand) ctx.list.accept(this);
        Operator op = ctx.NOT() == null ? Operator.IN : Operator.NOTIN;
        return new Predicate(ref, op, list);
    }

    @Override
    public ASTNode visitPredicateBetween(PredicateBetweenContext ctx) {
        var ref = (Operand) ctx.ref.accept(this);
        var min = (Literal) ctx.min.accept(this);
        var max = (Literal) ctx.max.accept(this);
        Operator op = ctx.NOT() == null ? Operator.BETWEEN : Operator.NOTBETWEEN;
        LiteralList range = new LiteralList(List.of(min, max));
        return new Predicate(ref, op, range);
    }

    @Override
    public ASTNode visitPredicateLike(PredicateLikeContext ctx) {
        var ref = (Operand) ctx.ref.accept(this);
        var s = (Operand) ctx.s.accept(this);
        boolean not = ctx.NOT() != null;
        boolean like = ctx.LIKE() != null;
        Operator op = not ? //
                (like ? Operator.NOTLIKE : Operator.NOTILIKE) : //
                (like ? Operator.LIKE : Operator.ILIKE);
        return new Predicate(ref, op, s);
    }

    @Override
    public ASTNode visitPredicateNull(PredicateNullContext ctx) {
        var ref = (Operand) ctx.ref.accept(this);
        Operator op = ctx.NOT() == null ? Operator.ISNULL : Operator.ISNOTNULL;
        return new Predicate(ref, op, null);
    }

    @Override
    public ASTNode visitPredicateStartsWith(PredicateStartsWithContext ctx) {
        var ref = (Operand) ctx.ref.accept(this);
        var s = (Operand) ctx.s.accept(this);
        return new Predicate(ref, Operator.STARTSWITH, s);
    }

    @Override
    public ASTNode visitPredicateParen(PredicateParenContext ctx) {
        return ctx.p.accept(this);
    }

    @Override
    public ASTNode visitPredicateNot(PredicateNotContext ctx) {
        var p = (Predicate) ctx.p.accept(this);
        return new Predicate(p, Operator.NOT, null);
    }

    @Override
    public ASTNode visitPredicateAnd(PredicateAndContext ctx) {
        return visitPredicateLogic(ctx.l, Operator.AND, ctx.r);
    }

    @Override
    public ASTNode visitPredicateOr(PredicateOrContext ctx) {
        return visitPredicateLogic(ctx.l, Operator.OR, ctx.r);
    }

    private ASTNode visitPredicateLogic(PredicateContext l, Operator op, PredicateContext r) {
        var left = (Operand) l.accept(this);
        var right = (Operand) r.accept(this);
        return new Predicate(left, op, right);
    }

    @Override
    public ASTNode visitExpressionOperand(ExpressionOperandContext ctx) {
        return ctx.o.accept(this);
    }

    @Override
    public ASTNode visitExpressionParen(ExpressionParenContext ctx) {
        return ctx.e.accept(this);
    }

    @Override
    public ASTNode visitExpressionMulDiv(ExpressionMulDivContext ctx) {
        return visitExpressionArithmetic(ctx.l, ctx.op, ctx.r);
    }

    @Override
    public ASTNode visitExpressionSumSub(ExpressionSumSubContext ctx) {
        return visitExpressionArithmetic(ctx.l, ctx.op, ctx.r);
    }

    private ASTNode visitExpressionArithmetic(ExpressionContext l, Token optok, ExpressionContext r) {
        var left = (Operand) l.accept(this);
        var right = (Operand) r.accept(this);
        Operator op = switch (optok.getType()) {
        case HXQLParser.MUL -> Operator.MUL;
        case HXQLParser.DIV -> Operator.DIV;
        case HXQLParser.SUM -> Operator.SUM;
        case HXQLParser.SUB -> Operator.SUB;
        default -> throw new IllegalStateException("Unexpected case: " + optok);
        };
        return new Expression(left, op, right);
    }

    @Override
    public ASTNode visitReferenceSimple(ReferenceSimpleContext ctx) {
        return ctx.ref.accept(this);
    }

    @Override
    public ASTNode visitReferenceDate(ReferenceDateContext ctx) {
        var ref = (Reference) ctx.ref.accept(this);
        return new Reference(ref, "DATE");
    }

    @Override
    public ASTNode visitReferenceHint(ReferenceHintContext ctx) {
        var ref = (Reference) ctx.ref.accept(this);
        var hint = (FtHint) ctx.hint.accept(this);
        return new Reference(ref, hint);
    }

    @Override
    public ASTNode visitSimple_reference(Simple_referenceContext ctx) {
        return new Reference(ctx.v.getText());
    }

    @Override
    public ASTNode visitOperand(OperandContext ctx) {
        if (ctx.v != null) {
            return ctx.v.accept(this);
        } else if (ctx.r != null) {
            return ctx.r.accept(this);
        } else {
            return ctx.f.accept(this);
        }
    }

    @Override
    public ASTNode visitLiteralString(LiteralStringContext ctx) {
        return ctx.v.accept(this);
    }

    @Override
    public ASTNode visitLiteralBoolean(LiteralBooleanContext ctx) {
        return ctx.v.getType() == HXQLParser.TRUE ? BooleanLiteral.LIT_TRUE : BooleanLiteral.LIT_FALSE;
    }

    @Override
    public ASTNode visitLiteralInteger(LiteralIntegerContext ctx) {
        return new IntegerLiteral(ctx.v.getText());
    }

    @Override
    public ASTNode visitLiteralDouble(LiteralDoubleContext ctx) {
        return new DoubleLiteral(ctx.v.getText());
    }

    @Override
    public ASTNode visitLiteralDate(LiteralDateContext ctx) {
        var sl = (StringLiteral) ctx.v.accept(this);
        return new DateLiteral(sl.asString(), true);
    }

    @Override
    public ASTNode visitLiteralTimestamp(LiteralTimestampContext ctx) {
        var sl = (StringLiteral) ctx.v.accept(this);
        return new DateLiteral(sl.asString(), false);
    }

    @Override
    public ASTNode visitString(StringContext ctx) {
        return new StringLiteral(parseString(ctx.s));
    }

    @Override
    public ASTNode visitLiteral_list(Literal_listContext ctx) {
        List<Literal> list = acceptList(ctx.literal());
        return new LiteralList(list);
    }

    @Override
    public ASTNode visitFunction(FunctionContext ctx) {
        String name = ctx.name.getText();
        var args = ctx.args == null ? null : (OperandList) ctx.args.accept(this);
        return new org.nuxeo.ecm.core.query.sql.model.Function(name, args);
    }

    @Override
    public ASTNode visitFunction_args(Function_argsContext ctx) {
        List<Operand> list = acceptList(ctx.expression());
        return new OperandList(list);
    }

    @Override
    public ASTNode visitFt_hint(Ft_hintContext ctx) {
        var index = ctx.index == null ? null : (FtIdentifierList) ctx.index.accept(this);
        String analyzer = ctx.analyzer == null ? null : ctx.analyzer.id.getText();
        String operator = ctx.operator == null ? null : ctx.operator.id.getText();
        if (index == null && analyzer == null && operator == null) {
            return null;
        }
        return new FtHint(index, analyzer, operator);
    }

    @Override
    public ASTNode visitFt_index(Ft_indexContext ctx) {
        return ctx.ft_identifier_list().accept(this);
    }

    @Override
    public ASTNode visitFt_identifier_list(Ft_identifier_listContext ctx) {
        List<String> list = ctx.ft_identifier().stream().map(c -> c.id.getText()).toList();
        return new FtIdentifierList(list);
    }

}
