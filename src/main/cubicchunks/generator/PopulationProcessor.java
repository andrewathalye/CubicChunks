/*
 *  This file is part of Cubic Chunks, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2014 Tall Worlds
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
package cubicchunks.generator;

import java.util.List;
import java.util.Random;
import net.minecraft.block.BlockFalling;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityTracker;
import net.minecraft.util.WeightedRandom;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import cubicchunks.CubeProviderTools;
import cubicchunks.generator.biome.CCBiomeManager;
import cubicchunks.generator.biome.biomegen.CCBiome;
import cubicchunks.generator.terrain.GlobalGeneratorConfig;
import cubicchunks.server.CubeWorldServer;
import cubicchunks.util.Coords;
import cubicchunks.util.CubeProcessor;
import cubicchunks.world.Cube;
import cubicchunks.world.CubeCache;

public class PopulationProcessor extends CubeProcessor {
	private final int seaLevel = 0;

	public PopulationProcessor(String name, CubeCache cache, int batchSize) {
		super(name, cache, batchSize);
	}

	@Override
	public boolean calculate(Cube cube) {
		int cubeX = cube.getX();
		int cubeY = cube.getY();
		int cubeZ = cube.getZ();

		if (!CubeProviderTools.cubeAndNeighborsExist(cache, cubeX, cubeY, cubeZ)) {
			return false;
		}

		if (!CubeProviderTools.checkGenerationStage(cache, GeneratorStage.Population, cubeX, cubeY, cubeZ,
				cubeX + 1, cubeY + 1, cubeZ + 1)) {
			return false;
		}

		World world = cube.getWorld();
		double heightMax = Double.NEGATIVE_INFINITY;
		double heightMin = Double.POSITIVE_INFINITY;
		double volMax = 0;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int biomeId = cube.getColumn().getBiomeMap()[z << 4 | x];
				CCBiome biome = CCBiome.getBiome(biomeId);

				double biomeHeight = 0.75 / 64.0 + biome.biomeHeight * 17.0 / 64.0;
				double biomeVolatility = 3 * (biome.biomeVolatility * 0.9 + 0.1);

				// +/- volatility +/. 1/64th (addHeight)
				double minBiomeHeight = biomeHeight - biomeVolatility / 4 - 1.0 / 64.0;
				double maxBiomdHeight = biomeHeight + biomeVolatility + 1.0 / 64.0;

				if (maxBiomdHeight > heightMax) {
					heightMax = maxBiomdHeight;
				}
				if (minBiomeHeight < heightMin) {
					heightMin = minBiomeHeight;
				}
			}
		}
		heightMin *= GlobalGeneratorConfig.maxElev;
		heightMax *= GlobalGeneratorConfig.maxElev;
		int buffer = 48;// 3 cubes
		CCBiomeManager wcm = (CCBiomeManager) cube.getWorld().getBiomeManager();

		int cubeMinBlock = Coords.cubeToMaxBlock(cube.getY());
		int cubeMaxBlock = Coords.cubeToMaxBlock(cube.getY());

		// BlockFalling.fallInstantly
		BlockFalling.field_149832_M = true;

		// if we are anywhere below max surface Y
		if (cubeMinBlock < heightMax) {
			calculateUnderground(cube, world, wcm);
		}
		// if we are anyywhere above min surface height
		if (cubeMaxBlock > heightMin) {
			calculateSky(cube, world, wcm);
		}
		// if any block of the cube is between min and max height
		if (cubeMinBlock <= heightMax && cubeMaxBlock >= heightMin) {
			calculateSurface(cube, world, wcm);
		}

		BlockFalling.field_149832_M = false;
		return true;
	}

	public void calculateUnderground(Cube cube, World world, CCBiomeManager wcm) {
		int cubeX = cube.getX();
		int cubeY = cube.getY();
		int cubeZ = cube.getZ();

		int xAbs = cubeX * 16;
		int yAbs = cubeY * 16;
		int zAbs = cubeZ * 16;

		// This can be easily replaced with an different biome definition for
		// underground decoration
		// CCBiome biome =
		// (CCBiome)world.getBiomeGenForCoords( xAbs + 16, zAbs + 16 );

		Random rand = new Random(world.getSeed());
		long rand1 = rand.nextLong() / 2L * 2L + 1L;
		long rand2 = rand.nextLong() / 2L * 2L + 1L;
		long rand3 = rand.nextLong() / 2L * 2L + 1L;
		rand.setSeed((long) cubeX * rand1 + (long) cubeY * rand2 + (long) cubeZ * rand3 ^ world.getSeed());

		// Start from center of cube
		int xCenter = xAbs + 8;
		int yCenter = yAbs + 8;
		int zCenter = zAbs + 8;

		int genX;
		int genY;
		int genZ;

		if (rand.nextInt(16) == 0) {
			genX = xCenter + rand.nextInt(16);
			genY = yCenter + rand.nextInt(16);
			genZ = zCenter + rand.nextInt(16);
			(new WorldGenLakes(Blocks.water)).generate(world, rand, genX, genY, genZ);
		}

		if (rand.nextInt(8) == 0) {
			if (rand.nextInt(Math.max(1, cubeY + 16 - m_seaLevel / 16)) == 0) {
				genX = xCenter + rand.nextInt(16);
				genY = yCenter + rand.nextInt(16);
				genZ = zCenter + rand.nextInt(16);

				if (genY < m_seaLevel || rand.nextInt(10) == 0) {
					(new WorldGenLakes(Blocks.lava)).generate(world, rand, genX, genY, genZ);
				}
			}
		}

		for (int i = 0; i < 8; ++i) {
			if (rand.nextInt(16) != 0) {
				continue;
			}
			genX = xCenter + rand.nextInt(16);
			genY = yCenter + rand.nextInt(16);
			genZ = zCenter + rand.nextInt(16);
			(new WorldGenDungeons()).generate(world, rand, genX, genY, genZ);
		}
		// Base parbability. In vanilla ores are generated in the whole comumn
		// at once, so probability togenerate them in one cube is 1/16.
		double probability = 1D / 16D;
		DecoratorHelper gen = new DecoratorHelper(world, rand, cubeX, cubeY, cubeZ);
		WorldGenMinable dirtGen = new WorldGenMinable(Blocks.dirt, 32);
		// WorldGenMinable gravelGen = new WorldGenMinable( Blocks.gravel, 32 );
		WorldGenMinable coalGen = new WorldGenMinable(Blocks.coal_ore, 16);
		WorldGenMinable ironGen = new WorldGenMinable(Blocks.iron_ore, 8);
		WorldGenMinable goldGen = new WorldGenMinable(Blocks.gold_ore, 8);
		WorldGenMinable redstoneGen = new WorldGenMinable(Blocks.redstone_ore, 7);
		WorldGenMinable diamondGen = new WorldGenMinable(Blocks.diamond_ore, 7);
		WorldGenMinable lapisGen = new WorldGenMinable(Blocks.lapis_ore, 6);
		gen.genberateAtRandomHeight(20, probability, dirtGen);// 0-256
		// Gravel generation disabled because of incredibly slow world saving.
		// this.generateAtRandomHeight( 10, probability, this.gravelGen,
		// maxTerrainY );//0-256
		// generate only in range 0-128. Doubled probability
		gen.generateAtRandomHeight(20, probability * 2, coalGen, 1.0D);// 0-128
		gen.generateAtRandomHeight(20, probability * 4, ironGen, 0.0D);// 0-64
		gen.generateAtRandomHeight(2, probability * 8, goldGen, -0.5D);// 0-32
		gen.generateAtRandomHeight(8, probability * 16, redstoneGen, -0.75D);// 0-16
		gen.generateAtRandomHeight(1, probability * 16, diamondGen, -0.75D);// 0-16
		gen.generateAtRandomHeight(1, probability * 8, lapisGen, -0.5D);// 0-32
	}

	public void calculateSky(Cube cube, World world, CCBiomeManager wcm) {
		// do nothing
	}

	public void calculateSurface(Cube cube, World world, CCBiomeManager wcm) {
		int cubeX = cube.getX();
		int cubeY = cube.getY();
		int cubeZ = cube.getZ();

		int xAbs = cubeX * 16;
		int yAbs = cubeY * 16;
		int zAbs = cubeZ * 16;

		CCBiome biome = (CCBiome) world.getBiomeGenForCoords(xAbs + 16, zAbs + 16);

		Random rand = new Random(world.getSeed());
		long rand1 = rand.nextLong() / 2L * 2L + 1L;
		long rand2 = rand.nextLong() / 2L * 2L + 1L;
		long rand3 = rand.nextLong() / 2L * 2L + 1L;
		rand.setSeed((long) cubeX * rand1 + (long) cubeY * rand2 + (long) cubeZ * rand3 ^ world.getSeed());

		boolean villageGenerated = false;
		// DON'T DO THIS YET
		/*
		 * if( m_mapFeaturesEnabled ) {
		 * m_mineshaftGenerator.generateStructuresInChunk( m_world, m_rand,
		 * cubeX, cubeZ ); var11 = m_villageGenerator.generateStructuresInChunk(
		 * m_world, m_rand, cubeX, cubeZ );
		 * m_strongholdGenerator.generateStructuresInChunk( m_world, m_rand,
		 * cubeX, cubeZ );
		 * m_scatteredFeatureGenerator.generateStructuresInChunk( m_world,
		 * m_rand, cubeX, cubeZ ); }
		 */

		// Start from center of cube
		int xCenter = xAbs + 8;
		int yCenter = yAbs + 8;
		int zCenter = zAbs + 8;

		int genX;
		int genY;
		int genZ;

		if (biome != CCBiome.desert && biome != CCBiome.desertHills && !villageGenerated
				&& rand.nextInt(4) == 0) {
			if (rand.nextInt(16) == 0) {
				genX = xCenter + rand.nextInt(16);
				genY = yCenter + rand.nextInt(16);
				genZ = zCenter + rand.nextInt(16);
				(new WorldGenLakes(Blocks.water)).generate(world, rand, genX, genY, genZ);
			}
		}

		if (!villageGenerated && rand.nextInt(8) == 0) {
			// var13 = m_rand.nextInt( m_rand.nextInt( 248 ) + 8 );

			if (rand.nextInt(Math.max(1, cubeY + 16 - m_seaLevel / 16)) == 0) {
				genX = xCenter + rand.nextInt(16);
				genY = yCenter + rand.nextInt(16);
				genZ = zCenter + rand.nextInt(16);

				if (genY < m_seaLevel || rand.nextInt(10) == 0) {
					(new WorldGenLakes(Blocks.lava)).generate(world, rand, genX, genY, genZ);
				}
			}
		}

		biome.decorate(world, rand, cubeX, cubeY, cubeZ);
		// TODO: cubify this:
		performWorldGenSpawning(world, biome, cube, xAbs, yAbs, zAbs, 16, 16, 16, rand);

		// double[][] temps = wcm.g( cubeX, cubeZ );
		/*
		 * for( int x = 0; x < 16; x++ ) { for( int z = 0; z < 16; z++ ) {
		 * LightIndex c = cube.getColumn().getLightIndex(); if( c == null ) {
		 * continue; } if( c.getTopNonTransparentBlockY( x, z ) == null ) {
		 * continue; } int y = c.getTopNonTransparentBlockY( x, z ); if(
		 * Coords.blockToCube( y ) != cubeY ) { continue; } // freeze the
		 * ocean/lakes if( cube.getBlock( x, Coords.blockToLocal( y ), z ) ==
		 * Blocks.water && temps[x][z] < 0.3D && y > m_seaLevel ) {
		 * cube.setBlock( x, Coords.blockToLocal( y ), z, Blocks.ice, 0 ); } //
		 * freeze pools else if( cube.getBlock( x, Coords.blockToLocal( y ), z )
		 * == Blocks.water && temps[x][z] < 0.2D && y <= m_seaLevel ) {
		 * cube.setBlock( x, Coords.blockToLocal( y ), z, Blocks.ice, 0 ); } //
		 * place snow else if( cube.getColumn().func_150810_a( x, y + 1, z ) ==
		 * Blocks.air && temps[x][z] < 0.34D && cube.getColumn().func_150810_a(
		 * x, y - 1, z ) != Blocks.water ) { cube.getColumn().func_150807_a( x,
		 * y + 1, z, Blocks.snow_layer, 0 ); } } }
		 */
	}

	public static void performWorldGenSpawning(World par0World, BiomeGenBase par1BiomeGenBase, Cube cube, int xMin,
			int yMin, int zMin, int xRange, int yRange, int zRange, Random par6Random) {
		List var7 = par1BiomeGenBase.getSpawnableList(EnumCreatureType.creature);
		if (!var7.isEmpty()) {
			while (par6Random.nextFloat() < par1BiomeGenBase.getSpawningChance()) {
				BiomeGenBase.SpawnListEntry var8 = (BiomeGenBase.SpawnListEntry) WeightedRandom.getRandomItem(
						par0World.rand, var7);
				IEntityLivingData var9 = null;
				int var10 = var8.minGroupCount + par6Random.nextInt(1 + var8.maxGroupCount - var8.minGroupCount);
				int x = xMin + par6Random.nextInt(xRange);
				// int y = yMin + par6Random.nextInt(yRange);
				int z = zMin + par6Random.nextInt(zRange);
				int var13 = x;
				int var14 = z;
				for (int var15 = 0; var15 < var10; ++var15) {
					boolean var16 = false;
					for (int var17 = 0; !var16 && var17 < 4; ++var17) {

						Integer var18 = cube.getColumn().getSkylightBlockY(x & 15, z & 15);
						if (var18 == null) {
							continue;
						}
						if (var18 == -1) {
							while (x < xMin || x >= xMin + xRange || z < zMin || z >= zMin + zRange) {
								x = var13 + par6Random.nextInt(5) - par6Random.nextInt(5);
								z = var14 + par6Random.nextInt(5) - par6Random.nextInt(5);
							}
							continue;
						}
						if (var18 > yMin + 16 || var18 < yMin) {
							while (x < xMin || x >= xMin + xRange || z < zMin || z >= zMin + zRange) {
								x = var13 + par6Random.nextInt(5) - par6Random.nextInt(5);
								z = var14 + par6Random.nextInt(5) - par6Random.nextInt(5);
							}
							continue;
						}
						if (SpawnerAnimals.canCreatureTypeSpawnAtLocation(EnumCreatureType.creature, par0World, x,
								var18, z)) {
							float var19 = (float) x + 0.5F;
							float var20 = (float) var18;
							float var21 = (float) z + 0.5F;
							EntityLiving var22;

							try {
								var22 = (EntityLiving) var8.entityClass.getConstructor(new Class[] { World.class })
										.newInstance(new Object[] { par0World });
							} catch (Exception var24) {
								var24.printStackTrace();
								continue;
							}

							var22.setLocationAndAngles((double) var19, (double) var20, (double) var21,
									par6Random.nextFloat() * 360.0F, 0.0F);
							par0World.spawnEntityInWorld(var22);
							var9 = var22.onSpawnWithEgg(var9);
							var16 = true;
						}

						x += par6Random.nextInt(5) - par6Random.nextInt(5);

						for (z += par6Random.nextInt(5) - par6Random.nextInt(5); x < xMin || x >= xMin + xRange
								|| z < zMin || z >= zMin + zRange; z = var14 + par6Random.nextInt(5)
								- par6Random.nextInt(5)) {
							x = var13 + par6Random.nextInt(5) - par6Random.nextInt(5);
						}
					}
				}
			}
		}
	}
}
