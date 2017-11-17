package com.ferreusveritas.dynamictrees.worldgen;

import java.util.ArrayList;
import java.util.Random;

import com.ferreusveritas.dynamictrees.ConfigHandler;
import com.ferreusveritas.dynamictrees.api.TreeHelper;
import com.ferreusveritas.dynamictrees.api.worldgen.IBiomeDensityProvider.EnumChance;
import com.ferreusveritas.dynamictrees.api.worldgen.IBiomeTreeSelector.Decision;
import com.ferreusveritas.dynamictrees.trees.DynamicTree;
import com.ferreusveritas.dynamictrees.util.Circle;

import net.minecraft.block.BlockColored;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.feature.WorldGenBigMushroom;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;
import net.minecraftforge.fml.common.IWorldGenerator;

public class TreeGenerator implements IWorldGenerator {
	
	public BiomeTreeHandler biomeTreeHandler; //Provides forest properties for a biome
	public BiomeRadiusCoordinator radiusCoordinator; //Finds radius for coordinates
	public TreeCodeStore codeStore;
	protected ChunkCircleManager circleMan;
	protected RandomXOR random;
	
	public enum EnumGeneratorResult {
		GENERATED(EnumDyeColor.WHITE),
		NOTREE(EnumDyeColor.BLACK),
		UNHANDLEDBIOME(EnumDyeColor.YELLOW),
		FAILSOIL(EnumDyeColor.BROWN),
		FAILCHANCE(EnumDyeColor.BLUE),
		FAILGENERATION(EnumDyeColor.RED);
		
		private final EnumDyeColor color;
		
		private EnumGeneratorResult(EnumDyeColor color) {
			this.color = color;
		}
		
		public EnumDyeColor getColor() {
			return this.color;
		}
	
	}
	
	public TreeGenerator() {
		biomeTreeHandler = new BiomeTreeHandler();
		radiusCoordinator = new BiomeRadiusCoordinator(biomeTreeHandler);
		circleMan = new ChunkCircleManager(radiusCoordinator);
		random = new RandomXOR();
	}
	
	public void onWorldUnload() {
		circleMan = new ChunkCircleManager(radiusCoordinator);//Clears the cached circles
	}
	
	public ChunkCircleManager getChunkCircleManager() {
		return circleMan;
	}
	
	@Override
	public void generate(Random randomUnused, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
		
		//We use this custom random number generator because despite what everyone says the Java Random class is not thread safe.
		random.setXOR(new BlockPos(chunkX, 0, chunkZ));
		
		switch (world.provider.getDimension()) {
		case 0: //Overworld
			generateOverworld(random, chunkX, chunkZ, world, chunkGenerator, chunkProvider);
			break;
		case -1: //Nether
			break;
		case 1: //End
			break;
		}
	}
	
	private void generateOverworld(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
		ArrayList<Circle> circles = circleMan.getCircles(world, random, chunkX, chunkZ);
		
		for(Circle c: circles) {
			makeTree(world, c);
		}
		
		BlockPos pos = new BlockPos(chunkX * 16, 0, chunkZ * 16);
		if(BiomeDictionary.isBiomeOfType(world.getBiome(pos), Type.SPOOKY)) {
			roofedForestCompensation(world, random, pos);
		}
	}
	
	/**
	 * Decorate the roofedForest exactly like Minecraft, except leave out the trees and just make giant mushrooms
	 * 
	 * @param world
	 * @param random
	 * @param pos
	 */
	public void roofedForestCompensation(World world, Random random, BlockPos pos) {
		for (int xi = 0; xi < 4; ++xi) {
			for (int zi = 0; zi < 4; ++zi) {
				int posX = xi * 4 + 1 + 8 + random.nextInt(3);
				int posZ = zi * 4 + 1 + 8 + random.nextInt(3);
				BlockPos blockpos = world.getHeight(pos.add(posX, 0, posZ));

				if (random.nextInt(16) == 0) {
					new WorldGenBigMushroom().generate(world, random, blockpos);
				}
			}
		}
	}
	
	public void makeWoolCircle(World world, Circle circle, int h, EnumGeneratorResult resultType) {
		makeWoolCircle(world, circle, h, resultType, 0);
	}
	
	public void makeWoolCircle(World world, Circle circle, int h, EnumGeneratorResult resultType, int flags) {
		//System.out.println("Making circle at: " + circle.x + "," + circle.z + ":" + circle.radius + " H: " + h);
		
		for(int ix = -circle.radius; ix <= circle.radius; ix++) {
			for(int iz = -circle.radius; iz <= circle.radius; iz++) {
				if(circle.isEdge(circle.x + ix, circle.z + iz)) {
					world.setBlockState(new BlockPos(circle.x + ix, h, circle.z + iz), Blocks.WOOL.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.byMetadata((circle.x ^ circle.z) & 0xF)), flags);
				}
			}
		}
		
		if(resultType != EnumGeneratorResult.GENERATED) {
			BlockPos pos = new BlockPos(circle.x, h, circle.z);
			EnumDyeColor color = resultType.getColor();
			world.setBlockState(pos, Blocks.STAINED_HARDENED_CLAY.getDefaultState().withProperty(BlockColored.COLOR, color));
			world.setBlockState(pos.up(), Blocks.STAINED_HARDENED_CLAY.getDefaultState().withProperty(BlockColored.COLOR, color));
		}
	}
	
	private EnumGeneratorResult makeTree(World world, Circle circle) {
		
		circle.add(8, 8);//Move the circle into the "stage"
		
		BlockPos pos = world.getHeight(new BlockPos(circle.x, 0, circle.z)).down();
		while(world.isAirBlock(pos) || TreeHelper.isTreePart(world, pos)) {//Skip down past the bits of generated tree and air
			pos = pos.down();
		}
		
		IBlockState blockState = world.getBlockState(pos);
		
		EnumGeneratorResult result = EnumGeneratorResult.GENERATED;
		
		Biome biome = world.getBiome(pos);
		Decision decision = biomeTreeHandler.getTree(world, biome, pos, blockState, random);
		if(decision.isHandled()) {
			DynamicTree tree = decision.getTree();
			if(tree != null) {
				if(tree.isAcceptableSoilForWorldgen(world, pos, blockState)) {
					if(biomeTreeHandler.chance(biome, tree, circle.radius, random) == EnumChance.OK) {
						if(tree.generate(world, pos, biome, random, circle.radius)) {
							result = EnumGeneratorResult.GENERATED;
						} else {
							result = EnumGeneratorResult.FAILGENERATION;
						}
					} else {
						result = EnumGeneratorResult.FAILCHANCE;
					}
				} else {
					result = EnumGeneratorResult.FAILSOIL;
				}
			} else {
				result = EnumGeneratorResult.NOTREE;
			}
		} else {
			result = EnumGeneratorResult.UNHANDLEDBIOME;
		}
		
		//Display wool circles for testing the circle growing algorithm
		if(ConfigHandler.worldGenDebug) {
			makeWoolCircle(world, circle, pos.getY(), result);
		}
		
		circle.add(-8, -8);//Move the circle back to normal coords
		
		return result;
	}
	
}