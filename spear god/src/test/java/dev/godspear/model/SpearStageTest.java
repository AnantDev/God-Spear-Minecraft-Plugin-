package dev.godspear.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpearStageTest {
    @Test void progressesExactlyOneStage(){assertEquals(SpearStage.STONE,SpearStage.WOOD.next());assertEquals(SpearStage.IRON,SpearStage.STONE.next());assertEquals(SpearStage.DIAMOND,SpearStage.IRON.next());assertEquals(SpearStage.NETHERITE,SpearStage.DIAMOND.next());assertEquals(SpearStage.GOD,SpearStage.NETHERITE.next());}
    @Test void godNeverEvolvesFurther(){assertEquals(SpearStage.GOD,SpearStage.GOD.next());}
    @Test void acceptsDocumentedNames(){assertEquals(SpearStage.WOOD,SpearStage.parse("wood"));assertEquals(SpearStage.WOOD,SpearStage.parse("wooden"));assertEquals(SpearStage.NETHERITE,SpearStage.parse("netherite"));}
}
