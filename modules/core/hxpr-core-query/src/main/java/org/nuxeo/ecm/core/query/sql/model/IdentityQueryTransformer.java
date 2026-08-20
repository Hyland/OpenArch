/*
 * (C) Copyright 2017-2026 Hyland (https://www.hyland.com/).
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
package org.nuxeo.ecm.core.query.sql.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

import org.nuxeo.ecm.core.query.QueryParseException;

/**
 * Transforms a query into the same query, doing a copy in the process.
 * <p>
 * Can be used as a base class for more complex transformations.
 *
 * @since 9.10
 */
public class IdentityQueryTransformer implements QueryTransformer {

    @Override
    public SQLQuery transform(SQLQuery node) {
        SelectClause select = transform(node.getSelectClause());
        FromClause from = transform(node.from);
        WhereClause where = transform(node.where);
        OrderByClause orderBy = transform(node.getOrderByClause());
        long limit = transformLimit(node.getLimit());
        long offset = transformOffset(node.getOffset());
        return new SQLQuery(select, from, where, orderBy, limit, offset);
    }

    @Override
    public SelectClause transform(SelectClause node) {
        return new SelectClause(transform(node.elements));
    }

    @Override
    public SelectList transform(SelectList node) {
        SelectList list = new SelectList();
        for (Operand operand : node) {
            list.add(transform(operand));
        }
        return list;
    }

    @Override
    public FromClause transform(FromClause node) {
        return new FromClause(transform(node.elements));
    }

    @Override
    public FromList transform(FromList node) {
        return new FromList(node);
    }

    @Override
    public WhereClause transform(WhereClause node) {
        return new WhereClause(transform(node.predicate));
    }

    @Override
    public OrderByClause transform(OrderByClause node) {
        if (node == null) {
            return null;
        }
        return new OrderByClause(transform(node.elements));
    }

    @Override
    public OrderByList transform(OrderByList node) {
        OrderByList list = new OrderByList();
        for (OrderByExpr value : node) {
            list.add(transform(value));
        }
        return list;
    }

    @Override
    public OrderByExpr transform(OrderByExpr node) {
        return new OrderByExpr(transform(node.reference), node.isDescending);
    }

    @Override
    public long transformLimit(long limit) {
        return limit;
    }

    @Override
    public long transformOffset(long offset) {
        return offset;
    }

    @Override
    public Operand transform(Operand node) {
        if (node instanceof Literal litNode) {
            return transform(litNode);
        } else if (node instanceof LiteralList litListNode) {
            return transform(litListNode);
        } else if (node instanceof Function funNode) {
            return transform(funNode);
        } else if (node instanceof MultiExpression multiNode) {
            return transform(multiNode);
        } else if (node instanceof Predicate preNode) {
            return transform(preNode);
        } else if (node instanceof Expression expNode) {
            return transform(expNode);
        } else if (node instanceof Reference refNode) {
            return transform(refNode);
        } else {
            throw new QueryParseException("Unknown operand: " + node);
        }
    }

    @Override
    public Expression transform(Expression node) {
        if (node instanceof Predicate preNode) {
            return transform(preNode);
        }
        Expression expr = new Expression(transform(node.lvalue), transform(node.operator), transform(node.rvalue));
        expr.setInfo(node.getInfo());
        return expr;
    }

    @Override
    public Predicate transform(Predicate node) {
        if (node instanceof MultiExpression multiNode) {
            return transform(multiNode);
        }
        Predicate predicate = new Predicate(transform(node.lvalue), transform(node.operator), transform(node.rvalue));
        predicate.setInfo(node.getInfo());
        return predicate;
    }

    @Override
    public MultiExpression transform(MultiExpression node) {
        List<Predicate> predicates = new ArrayList<>(node.predicates.size());
        for (Predicate predicate : node.predicates) {
            predicates.add(transform(predicate));
        }
        MultiExpression expr = new MultiExpression(transform(node.operator), predicates);
        expr.setInfo(node.getInfo());
        return expr;
    }

    @Override
    public Operator transform(Operator node) {
        return node;
    }

    @Override
    public Reference transform(Reference node) {
        Reference ref = new Reference(node.name, node.cast, node.ftHint);
        ref.setInfo(node.getInfo());
        return ref;
    }

    @Override
    public ReferenceList transform(ReferenceList node) {
        ReferenceList list = new ReferenceList();
        for (Reference ref : node) {
            list.add(transform(ref));
        }
        return list;
    }

    @Override
    public Operand transform(Function node) {
        return new Function(node.name, transform(node.args));
    }

    @Override
    public OperandList transform(OperandList node) {
        OperandList list = new OperandList();
        for (Operand oper : node) {
            list.add(transform(oper));
        }
        return list;
    }

    @Override
    public Literal transform(Literal node) {
        return switch (node) {
            case BooleanLiteral boolNode -> transform(boolNode);
            case DateLiteral dateNode -> transform(dateNode);
            case DoubleLiteral doubleNode -> transform(doubleNode);
            case IntegerLiteral intNode -> transform(intNode);
            case StringLiteral strNode -> transform(strNode);
        };
    }

    @Override
    public LiteralList transform(LiteralList node) {
        LiteralList list = new LiteralList();
        for (Literal lit : node) {
            list.add(transform(lit));
        }
        return list;
    }

    @Override
    public Literal transform(BooleanLiteral node) {
        return node;
    }

    @Override
    public Literal transform(DateLiteral node) {
        return node;
    }

    @Override
    public Literal transform(DoubleLiteral node) {
        return node;
    }

    @Override
    public Literal transform(IntegerLiteral node) {
        return node;
    }

    @Override
    public Literal transform(StringLiteral node) {
        return node;
    }

}
