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
package com.hyland.openarch.ecm.core.query.sql.parser;


import java.util.List;
import java.util.function.Function;

import static java.util.stream.Collectors.joining;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import com.hyland.openarch.ecm.core.query.QueryParseException;
import com.hyland.openarch.ecm.core.query.sql.model.ASTNode;
import com.hyland.openarch.ecm.core.query.sql.model.BooleanLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.DateLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.DoubleLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.FromClause;
import com.hyland.openarch.ecm.core.query.sql.model.FtHint;
import com.hyland.openarch.ecm.core.query.sql.model.IntegerLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.Literal;
import com.hyland.openarch.ecm.core.query.sql.model.LiteralList;
import com.hyland.openarch.ecm.core.query.sql.model.Operand;
import com.hyland.openarch.ecm.core.query.sql.model.OperandList;
import com.hyland.openarch.ecm.core.query.sql.model.Operator;
import com.hyland.openarch.ecm.core.query.sql.model.OrderByClause;
import com.hyland.openarch.ecm.core.query.sql.model.OrderByExpr;
import com.hyland.openarch.ecm.core.query.sql.model.Parameter;
import com.hyland.openarch.ecm.core.query.sql.model.Predicate;
import com.hyland.openarch.ecm.core.query.sql.model.Reference;
import com.hyland.openarch.ecm.core.query.sql.model.SelectClause;
import com.hyland.openarch.ecm.core.query.sql.model.Statement;
import com.hyland.openarch.ecm.core.query.sql.model.StringLiteral;
import com.hyland.openarch.ecm.core.query.sql.model.WhereClause;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.ExpressionOperandContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.ExpressionParenContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.From_clauseContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.Ft_hintContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.Ft_identifier_listContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.FunctionContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.Function_argsContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.LiteralBooleanContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.LiteralDateContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.LiteralDoubleContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.LiteralIntegerContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.LiteralStringContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.LiteralTimestampContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.OperandContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.Order_by_clauseContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.Order_by_exprContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.ParameterContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.ParseContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.Parse_predicateContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.Parse_referenceContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.PredicateAndContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.PredicateBetweenContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.PredicateCompareContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.PredicateContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.PredicateInContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.PredicateLikeContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.PredicateNotContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.PredicateNullContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.PredicateOrContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.PredicateParenContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.PredicateStartsWithContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.QueryContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.ReferenceDateContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.ReferenceHintContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.ReferenceSimpleContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.Select_clauseContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.Simple_referenceContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.StringContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.ValueContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.Value_listContext;
import com.hyland.openarch.ecm.core.query.sql.parser.HXQLParser.Where_clauseContext;

public class HXQLVisitor extends HXQLParserBaseVisitor<ASTNode> {

    private static class ErrorListener extends BaseErrorListener {

        public static final ErrorListener INSTANCE = new ErrorListener();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
                String msg, RecognitionException e) throws ParseCancellationException {
            throw new ParseCancellationException("At position " + line + ":" + charPositionInLine + " " + msg);
        }
    }

    public Statement parse(String string) throws QueryParseException {
        return parse(string, HXQLParser::parse);
    }

    /**
     * Parses a bare {@code WHERE} clause fragment, without the {@code WHERE} keyword.
     *
     * @since 12.0
     */
    public Predicate parsePredicate(String string) throws QueryParseException {
        return parse(string, HXQLParser::parse_predicate);
    }

    /**
     * Parses a field name on its own.
     *
     * @since 12.0
     */
    public Reference parseReference(String string) throws QueryParseException {
        return parse(string, HXQLParser::parse_reference);
    }

    @SuppressWarnings("unchecked")
    private <T extends ASTNode> T parse(String string, Function<HXQLParser, ParserRuleContext> rule)
            throws QueryParseException {
        try {
            HXQLLexer lexer = new HXQLLexer(CharStreams.fromString(string));
            lexer.removeErrorListeners();
            lexer.addErrorListener(ErrorListener.INSTANCE);
            HXQLParser parser = new HXQLParser(new CommonTokenStream(lexer));
            parser.removeErrorListeners();
            parser.addErrorListener(ErrorListener.INSTANCE);
            return (T) visit(rule.apply(parser));
        } catch (ParseCancellationException e) {
            throw new QueryParseException(e.getMessage(), e);
        }
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

    @Override
    public ASTNode visitParse(ParseContext ctx) {
        return ctx.q.accept(this);
    }

    @Override
    public ASTNode visitParse_predicate(Parse_predicateContext ctx) {
        return ctx.p.accept(this);
    }

    @Override
    public ASTNode visitParse_reference(Parse_referenceContext ctx) {
        return ctx.r.accept(this);
    }

    @Override
    public ASTNode visitQuery(QueryContext ctx) {
        var select = (SelectClause) ctx.select.accept(this);
        var from = (FromClause) ctx.from.accept(this);
        var where = ctx.where == null ? null : (WhereClause) ctx.where.accept(this);
        var orderBy = ctx.orderBy == null ? null : (OrderByClause) ctx.orderBy.accept(this);
        return new Statement(select, from, where, orderBy);
    }

    @Override
    public ASTNode visitSelect_clause(Select_clauseContext ctx) {
        // a MUL projection is SELECT *, which is an empty projection
        List<Operand> operands = ctx.list.MUL() != null ? List.of() : acceptList(ctx.list.operand());
        return new SelectClause(operands);
    }

    @Override
    public ASTNode visitFrom_clause(From_clauseContext ctx) {
        return new FromClause(ctx.list.from_elem().stream().map(c -> c.id.getText()).toList());
    }

    @Override
    public ASTNode visitWhere_clause(Where_clauseContext ctx) {
        var predicate = (Predicate) ctx.e.accept(this);
        return new WhereClause(predicate);
    }

    @Override
    public ASTNode visitOrder_by_clause(Order_by_clauseContext ctx) {
        return new OrderByClause(acceptList(ctx.list.order_by_expr()));
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
        var list = (Operand) (ctx.list != null ? ctx.list.accept(this) : ctx.param.accept(this));
        Operator op = ctx.NOT() == null ? Operator.IN : Operator.NOTIN;
        return new Predicate(ref, op, list);
    }

    @Override
    public ASTNode visitPredicateBetween(PredicateBetweenContext ctx) {
        var ref = (Operand) ctx.ref.accept(this);
        var min = (Operand) ctx.min.accept(this);
        var max = (Operand) ctx.max.accept(this);
        Operator op = ctx.NOT() == null ? Operator.BETWEEN : Operator.NOTBETWEEN;
        return new Predicate(ref, op, operandsToList(List.of(min, max)));
    }

    @Override
    public ASTNode visitPredicateLike(PredicateLikeContext ctx) {
        var ref = (Operand) ctx.ref.accept(this);
        var s = (Operand) (ctx.s != null ? ctx.s.accept(this) : ctx.param.accept(this));
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
        var s = (Operand) (ctx.s != null ? ctx.s.accept(this) : ctx.param.accept(this));
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
        return new Reference(ref, parseFtHint(ctx.hint));
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
        } else if (ctx.f != null) {
            return ctx.f.accept(this);
        } else {
            return ctx.p.accept(this);
        }
    }

    @Override
    public ASTNode visitParameter(ParameterContext ctx) {
        // strip the leading colon
        return new Parameter(ctx.v.getText().substring(1));
    }

    @Override
    public ASTNode visitValue(ValueContext ctx) {
        return ctx.v != null ? ctx.v.accept(this) : ctx.p.accept(this);
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
    public ASTNode visitValue_list(Value_listContext ctx) {
        List<Operand> list = acceptList(ctx.value());
        return operandsToList(list);
    }

    /**
     * Wraps values in a {@link LiteralList} if they are all literals, and in an {@link OperandList} if any of them is
     * still an unbound {@link Parameter}. Binding turns the latter back into a {@link LiteralList}.
     */
    protected static Operand operandsToList(List<Operand> operands) {
        if (operands.stream().allMatch(Literal.class::isInstance)) {
            return new LiteralList(operands.stream().map(Literal.class::cast).toList());
        }
        return new OperandList(operands);
    }

    @Override
    public ASTNode visitFunction(FunctionContext ctx) {
        String name = ctx.name.getText();
        var args = ctx.args == null ? null : (OperandList) ctx.args.accept(this);
        return new com.hyland.openarch.ecm.core.query.sql.model.Function(name, args);
    }

    @Override
    public ASTNode visitFunction_args(Function_argsContext ctx) {
        List<Operand> list = acceptList(ctx.expression());
        return new OperandList(list);
    }

    /** A hint is not an AST node of its own, it decorates a reference, so it is built directly. */
    protected FtHint parseFtHint(Ft_hintContext ctx) {
        String index = ctx.index == null ? null : ftIdentifiers(ctx.index.ft_identifier_list());
        String analyzer = ctx.analyzer == null ? null : ctx.analyzer.id.getText();
        String operator = ctx.operator == null ? null : ctx.operator.id.getText();
        if (index == null && analyzer == null && operator == null) {
            return null;
        }
        return new FtHint(index, analyzer, operator);
    }

    /** The comma-separated field names of a hint's INDEX(...). */
    protected static String ftIdentifiers(Ft_identifier_listContext ctx) {
        return ctx.ft_identifier().stream().map(c -> c.id.getText()).collect(joining(","));
    }

}
