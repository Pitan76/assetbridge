package net.pitan76.assetbridge.shape;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeBoxTest {
    /** A ladder sits on the south wall; the blockstate turns it a quarter to reach the west one. */
    @Test
    void aQuarterTurnAroundYMovesTheSouthFaceToTheWest() {
        ShapeBox south = new ShapeBox(0, 0, 13, 16, 16, 16);

        assertEquals(new ShapeBox(0, 0, 0, 3, 16, 16), south.rotateY(90));
    }

    @Test
    void aHalfTurnAroundYFacesTheOppositeWay() {
        ShapeBox south = new ShapeBox(0, 0, 13, 16, 16, 16);

        assertEquals(new ShapeBox(0, 0, 0, 16, 16, 3), south.rotateY(180));
    }

    /** How a trapdoor's blockstate turns the bottom model into the top one. */
    @Test
    void aHalfTurnAroundXPutsTheFloorOnTheCeiling() {
        ShapeBox bottom = new ShapeBox(0, 0, 0, 16, 3, 16);

        assertEquals(new ShapeBox(0, 13, 0, 16, 16, 16), bottom.rotateX(180));
    }

    @Test
    void aQuarterTurnAroundXTipsTheBlockTowardsTheNorth() {
        ShapeBox bottom = new ShapeBox(0, 0, 0, 16, 3, 16);

        assertEquals(new ShapeBox(0, 0, 13, 16, 16, 16), bottom.rotateX(90));
    }

    @Test
    void anAngleThatIsNotAQuarterTurnLeavesTheBoxAlone() {
        ShapeBox box = new ShapeBox(0, 0, 13, 16, 16, 16);

        assertEquals(box, box.rotateY(45));
    }

    @Test
    void clampingCutsAwayWhatIsOutsideTheBlock() {
        ShapeBox overhanging = new ShapeBox(-4, 0, 0, 20, 8, 16);

        assertEquals(new ShapeBox(0, 0, 0, 16, 8, 16), overhanging.clamped());
    }

    @Test
    void clampingDropsABoxThatIsEntirelyOutside() {
        assertNull(new ShapeBox(20, 0, 0, 24, 8, 16).clamped());
    }

    @Test
    void knowsAFullCube() {
        assertTrue(new ShapeBox(0, 0, 0, 16, 16, 16).isFullCube());
        assertFalse(new ShapeBox(0, 0, 0, 16, 8, 16).isFullCube());
    }
}
