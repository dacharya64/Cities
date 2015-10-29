/*
 * Copyright 2015 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.cities.bldg.gen;

import java.math.RoundingMode;

import org.terasology.cities.BlockTypes;
import org.terasology.cities.bldg.Building;
import org.terasology.cities.bldg.BuildingPart;
import org.terasology.cities.bldg.DefaultBuilding;
import org.terasology.cities.bldg.RectBuildingPart;
import org.terasology.cities.common.Edges;
import org.terasology.cities.door.WingDoor;
import org.terasology.cities.model.roof.HipRoof;
import org.terasology.cities.model.roof.PentRoof;
import org.terasology.cities.model.roof.SaddleRoof;
import org.terasology.cities.parcels.Parcel;
import org.terasology.cities.surface.InfiniteSurfaceHeightFacet;
import org.terasology.cities.window.RectWindow;
import org.terasology.commonworld.Orientation;
import org.terasology.math.TeraMath;
import org.terasology.math.geom.LineSegment;
import org.terasology.math.geom.Rect2i;
import org.terasology.math.geom.Vector2i;
import org.terasology.utilities.random.MersenneRandom;
import org.terasology.utilities.random.Random;

/**
 * Creates building models of a simple church.
 */
public class SimpleChurchGenerator {

    private final long seed;

    /**
     * @param seed the seed
     */
    public SimpleChurchGenerator(long seed) {
        this.seed = seed;
    }

    /**
     * @param lot the lot to use
     * @param hm the height map to define the floor level
     * @return a generated building model of a simple church
     */
    public Building apply(Parcel lot, InfiniteSurfaceHeightFacet hm) {

        Random rand = new MersenneRandom(seed ^ lot.getShape().hashCode());

        // make build-able area 1 block smaller, so make the roof stay inside
        Rect2i lotRc = lot.getShape().expand(new Vector2i(-1, -1));

        boolean alignEastWest = (lotRc.width() > lotRc.height());
        Orientation o = alignEastWest ? Orientation.EAST : Orientation.NORTH;

        if (rand.nextBoolean()) {
            o = o.getOpposite();
        }

        Turtle turtle = new Turtle(Edges.getCorner(lotRc, o.getOpposite()), o);
        int length = turtle.length(lotRc);
        int width = turtle.width(lotRc);

        double relationWidth = 2.0;

        int towerSize = (int) (length * 0.2);  // tower size compared to nave size

        // make it odd, so that the tented roof looks nice (1 block thick at the center)
        if (towerSize % 2 == 0) {
            towerSize++;
        }

        int sideOff = 3;
        int sideWidth = 5;

        int naveLen = length - towerSize;
        int naveWidth = (int) Math.min(width - 2 * sideWidth, towerSize * relationWidth);
        int sideLen = naveLen - 2 * sideOff;

        // make it odd, so it looks symmetric with the tower - make it smaller though
        if (naveWidth % 2 == 0) {
            naveWidth--;
        }

        int entranceWidth = 3;  // odd number to center properly
        Rect2i entranceRect = turtle.rectCentered(0, entranceWidth, 1);

        Rect2i naveRect = turtle.rectCentered(0, naveWidth, naveLen);
        Rect2i towerRect = turtle.rectCentered(naveLen - 1, towerSize, towerSize); // the -1 makes tower and nave overlap
        int baseHeight = getMaxHeight(entranceRect, hm) + 1; // 0 == terrain

        DefaultBuilding church = new DefaultBuilding(turtle.getOrientation());
        church.addPart(createNave(new Turtle(turtle), naveRect, entranceRect, baseHeight));
        church.addPart(createTower(new Turtle(turtle), towerRect, baseHeight));

        Rect2i aisleLeftRc = turtle.rect(-naveWidth / 2 - sideWidth + 1, sideOff, sideWidth, sideLen);  // make them overlap
        Rect2i aisleRightRc = turtle.rect(naveWidth / 2, sideOff, sideWidth, sideLen); // make them overlap

        church.addPart(createAisle(new Turtle(turtle).rotate(-90), aisleLeftRc, baseHeight));
        church.addPart(createAisle(new Turtle(turtle).rotate(90), aisleRightRc, baseHeight));

        return church;
    }

    private BuildingPart createNave(Turtle cur, Rect2i naveRect, Rect2i doorRc, int baseHeight) {
        int entranceHeight = 4;

        int hallHeight = 9;

        Rect2i naveRoofRect = naveRect.expand(1, 1);
        SaddleRoof naveRoof = new SaddleRoof(naveRoofRect, baseHeight + hallHeight, cur.getOrientation(), 1);

        RectBuildingPart nave = new RectBuildingPart(naveRect, naveRoof, baseHeight, hallHeight);

        WingDoor entrance = new WingDoor(cur.getOrientation(), doorRc, baseHeight, baseHeight + entranceHeight);
        nave.addDoor(entrance);

        return nave;
    }

    private BuildingPart createTower(Turtle turtle, Rect2i rect, int baseHeight) {
        int towerHeight = 22;
        int doorHeight = 5;

        Orientation dir = turtle.getOrientation();
        Rect2i towerRoofRect = rect.expand(1, 1);
        HipRoof towerRoof = new HipRoof(towerRoofRect, baseHeight + towerHeight, 2);
        RectBuildingPart tower = new RectBuildingPart(rect, towerRoof, baseHeight, towerHeight);

        turtle.setPosition(Edges.getCorner(rect, dir.getOpposite()));

        int width = turtle.width(rect) - 2;
        tower.addDoor(new WingDoor(dir, turtle.rectCentered(0, width, 1), baseHeight, baseHeight + doorHeight));

        // create and add tower windows
        for (int i = 0; i < 3; i++) {
            // use the other three cardinal directions to place windows
            Orientation orient = dir.getRotated(90 * (i - 1)); // left, forward, right
            LineSegment towerBorder = Edges.getEdge(rect, orient);
            Vector2i towerPos = new Vector2i(towerBorder.lerp(0.5f), RoundingMode.HALF_UP);

            Rect2i wndRect = Rect2i.createFromMinAndSize(towerPos.getX(), towerPos.getY(), 1, 1);
            tower.addWindow(new RectWindow(orient, wndRect, baseHeight + towerHeight - 3, baseHeight + towerHeight - 1, BlockTypes.AIR));
        }

        return tower;
    }

    private RectBuildingPart createAisle(Turtle turtle, Rect2i rect, int baseHeight) {
        Rect2i roofRect = turtle.adjustRect(rect, -1, 1, 1, 1);  // back overlap +1 to not intersect with nave

        int sideWallHeight = 4;
        int doorHeight = sideWallHeight - 1;
        Orientation dir = turtle.getOrientation();
        Orientation roofOrient = dir.getOpposite();
        PentRoof roof = new PentRoof(roofRect, baseHeight + sideWallHeight, roofOrient, 0.5);
        RectBuildingPart aisle = new RectBuildingPart(rect, roof, baseHeight, sideWallHeight);

        turtle.setPosition(Edges.getCorner(rect, dir.getOpposite()));

        int len = turtle.width(rect) / 2 - 2;
        aisle.addDoor(new WingDoor(dir, turtle.rect(-len+1, 0, 3, 1), baseHeight, baseHeight + doorHeight));
        aisle.addDoor(new WingDoor(dir, turtle.rect(-1, 0, 3, 1), baseHeight, baseHeight + doorHeight));
        aisle.addDoor(new WingDoor(dir, turtle.rect(len-3, 0, 3, 1), baseHeight, baseHeight + doorHeight));

        return aisle;
    }

    private int getMaxHeight(Rect2i rc, InfiniteSurfaceHeightFacet hm) {
        int maxHeight = Integer.MIN_VALUE;

        for (int z = rc.minY(); z <= rc.maxY(); z++) {
            for (int x = rc.minX(); x <= rc.maxX(); x++) {
                int height = TeraMath.floorToInt(hm.getWorld(x, z));
                if (maxHeight < height) {
                    maxHeight = height;
                }
            }
        }

        return maxHeight;
    }
}
