/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 *
 */
package org.nuxeo.ecm.core.query.sql.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:fg@nuxeo.com">Florent Guillaume</a>
 */
public class TestLiterals {

    @Test
    public void testStringLiteral() {
        StringLiteral l;
        l = new StringLiteral("foo");
        assertEquals("'foo'", l.toString());
    }

    @Test
    public void testIntegerLiteral() {
        IntegerLiteral l;
        l = new IntegerLiteral("123");
        assertEquals("123", l.toString());
        l = new IntegerLiteral("-123");
        assertEquals("-123", l.toString());
    }

    @Test
    public void testDoubleLiteral() {
        DoubleLiteral l;
        l = new DoubleLiteral("1.23");
        assertEquals("1.23", l.toString());
        l = new DoubleLiteral(".2");
        assertEquals("0.2", l.toString());
        l = new DoubleLiteral(new BigDecimal("1234567890.1234567890123456789"));
        assertEquals("1.2345678901234567E9", l.toString()); // loses precision, we don't have a DecimalLiteral
    }

    @Test
    public void testDateLiteral() {
        DateLiteral d;
        d = new DateLiteral("2007-01-02", true);
        assertEquals("DATE '2007-01-02'", d.toString());
        d = new DateLiteral("2007-01-30T01:02:03+04:56", false); // with T
        assertEquals("TIMESTAMP '2007-01-29T20:06:03.000Z'", d.toString());
        d = new DateLiteral("2007-01-30 01:02:03+04:56", false); // with space
        assertEquals("TIMESTAMP '2007-01-29T20:06:03.000Z'", d.toString());
    }

    @Test
    public void testBooleanLiteral() {
        BooleanLiteral b = new BooleanLiteral(true);
        assertEquals("true", b.asString());
        assertEquals("true", b.toString());
        assertEquals(true, b.equals(b));
        assertEquals(true, b.equals(new BooleanLiteral(true)));
        assertNotEquals(true, b.equals(new BooleanLiteral(false)));
        assertNotEquals(true, b.equals("true"));
        assertEquals(1231, b.hashCode());
    }

    @Test
    public void testLiterals() {
        BooleanLiteral b = new BooleanLiteral(false);
        DoubleLiteral d = new DoubleLiteral(3.14);
        IntegerLiteral i = new IntegerLiteral(15);
        StringLiteral s = new StringLiteral("lorem ipsum");
        Instant calendar = Instant.parse("2022-08-28T00:00:00Z");
        DateLiteral dl = new DateLiteral(ZonedDateTime.ofInstant(calendar, ZoneOffset.UTC));

        // Test valueOf
        assertEquals(false, Literals.valueOf(b));
        assertEquals((Double) 3.14, Literals.valueOf(d));
        assertEquals("lorem ipsum", Literals.valueOf(s));

        Literal lit = b;
        assertEquals(false, Literals.valueOf(lit));
        /*lit = dl;
        assertEquals(dl, l.valueOf(lit));*/
        lit = d;
        assertEquals(3.14, Literals.valueOf(lit));
        lit = i;
        assertEquals(15l, Literals.valueOf(lit));
        lit = s;
        assertEquals("lorem ipsum", Literals.valueOf(lit));

        // Test toLiteral
        assertEquals(b, Literals.toLiteral(false));
        assertEquals(dl, Literals.toLiteral(calendar));
        ZonedDateTime zDT = ZonedDateTime.now();
        dl = new DateLiteral(zDT);
        assertEquals(dl, Literals.toLiteral(zDT));
        assertEquals(d, Literals.toLiteral(3.14));
        DoubleLiteral f = new DoubleLiteral(3.14f);
        assertEquals(f, Literals.toLiteral(3.14f));
        assertEquals(i, Literals.toLiteral(15));
        assertEquals(i, Literals.toLiteral(15l));
        assertEquals(s, Literals.toLiteral("lorem ipsum"));
    }

}
