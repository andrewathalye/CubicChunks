/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package cubicchunks.world.type;

import cubicchunks.CubicChunks;
import cubicchunks.util.Box;
import cubicchunks.util.CubePos;
import cubicchunks.util.IntRange;
import cubicchunks.world.ICubicWorld;
import cubicchunks.world.cube.Cube;
import cubicchunks.worldgen.generator.BasicCubeGenerator;
import cubicchunks.worldgen.generator.CubePrimer;
import cubicchunks.worldgen.generator.ICubeGenerator;
import cubicchunks.worldgen.generator.ICubePrimer;
import cubicchunks.worldgen.generator.custom.CustomGeneratorSettings;
import cubicchunks.worldgen.generator.custom.CustomTerrainGenerator;
import cubicchunks.worldgen.gui.CustomCubicGui;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiErrorScreen;
import net.minecraft.init.Biomes;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.gen.layer.GenLayer;
import net.minecraft.world.gen.layer.IntCache;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Field;
import java.util.ArrayList;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CustomCubicWorldType extends WorldType implements ICubicWorldType {

    private CustomCubicWorldType() {
        super("CustomCubic");
    }

    public static void create() {
        new CustomCubicWorldType();
    }

    @Override public IntRange calculateGenerationHeightRange(WorldServer world) {
        String string = world.getWorldInfo().getGeneratorOptions();
        CustomGeneratorSettings opts = CustomGeneratorSettings.fromJson(string);
        return new IntRange(opts.getMinHeight(), opts.getRealMaxHeight());
    }

    public BiomeProvider getBiomeProvider(World world) {
        if ("true".equalsIgnoreCase(System.getProperty("cubicchunks.debug.biomes"))) {
            return new BiomeProvider() {{
                this.genBiomes = new GenLayerDebug(4);
                this.biomeIndexLayer = new GenLayerDebug(4 + 2);
            }};
        } else {
            return super.getBiomeProvider(world);
        }
    }

    @Override
    public ICubeGenerator createCubeGenerator(ICubicWorld world) {
        return new CustomTerrainGenerator(world, world.getSeed());
    }

    public boolean isCustomizable() {
        return true;
    }

    @SideOnly(Side.CLIENT)
    public void onCustomizeButton(Minecraft mc, GuiCreateWorld guiCreateWorld) {
        if (Loader.isModLoaded("malisiscore")) {
            new CustomCubicGui(guiCreateWorld).display();
        } else {
            mc.displayGuiScreen(new GuiErrorScreen("MalisisCore not found!",
                    "You need to install MalisisCore version at least " + CubicChunks
                            .MALISIS_VERSION + " to use world customization"));
        }
    }

    private static class GenLayerDebug extends GenLayer {

        private final ArrayList<Biome> biomes;
        private int scaleBits;

        public GenLayerDebug(int scaleBits) {
            super(0);
            this.scaleBits = scaleBits;

            this.biomes = new ArrayList<>();
            // use reflection to get all biomes
            for (Field fld : Biomes.class.getDeclaredFields()) {
                if (Biome.class.isAssignableFrom(fld.getType())) {
                    try {
                        Biome b = (Biome) fld.get(null);
                        if (b != null) {
                            this.biomes.add(b);
                        }

                    } catch (IllegalAccessException e) {
                        throw new Error(e);
                    }
                }
            }
        }

        @Override
        public int[] getInts(int areaX, int areaY, int areaWidth, int areaHeight) {
            int[] biomes = IntCache.getIntCache(areaWidth * areaHeight);
            for (int offY = 0; offY < areaHeight; ++offY) {
                for (int offX = 0; offX < areaWidth; ++offX) {
                    int index = (offX + areaX) >> scaleBits;
                    index = Math.floorMod(index, this.biomes.size());
                    Biome biome = this.biomes.get(index);
                    biomes[offX + offY * areaWidth] = Biome.getIdForBiome(biome);
                }
            }
            return biomes;
        }
    }

}
