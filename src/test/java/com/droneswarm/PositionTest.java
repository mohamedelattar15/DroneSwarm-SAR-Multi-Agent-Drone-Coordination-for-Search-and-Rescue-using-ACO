package com.droneswarm;

import environment.Position;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour Position.
 */
class PositionTest {

    @Test
    void testEquals() {
        Position p1 = new Position(5, 10);
        Position p2 = new Position(5, 10);
        Position p3 = new Position(10, 5);
        assertEquals(p1, p2);
        assertNotEquals(p1, p3);
    }

    @Test
    void testDistance() {
        Position p1 = new Position(0, 0);
        Position p2 = new Position(3, 4);
        assertEquals(5.0, p1.distanceTo(p2), 0.001);
    }

    @Test
    void testManhattan() {
        Position p1 = new Position(0, 0);
        Position p2 = new Position(3, 4);
        assertEquals(7, p1.manhattanTo(p2));
    }

    @Test
    void testHashCode() {
        Position p1 = new Position(5, 10);
        Position p2 = new Position(5, 10);
        assertEquals(p1.hashCode(), p2.hashCode());
    }
}
