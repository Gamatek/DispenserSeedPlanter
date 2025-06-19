package com.gamatek.dispenserseedplanter;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional; // For getting dispenser and setting cocoa facing
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet; // For efficient sets of materials
import java.util.Set;

public class DispenserSeedPlanter extends JavaPlugin implements Listener {

    // Pre-define sets of materials for cleaner checks and efficiency
    private static final Set<Material> FARMABLE_SOILS = EnumSet.of(
            Material.DIRT, Material.GRASS_BLOCK, Material.PODZOL, Material.COARSE_DIRT,
            Material.ROOTED_DIRT, Material.MUD
    );

    private static final Set<Material> SAPLING_SOILS = EnumSet.of(
            Material.DIRT, Material.GRASS_BLOCK, Material.PODZOL, Material.COARSE_DIRT,
            Material.ROOTED_DIRT, Material.MOSS_BLOCK, Material.MUD // Moss Block for some saplings
    );

    private static final Set<Material> SUGAR_CANE_SOILS = EnumSet.of(
            Material.DIRT, Material.GRASS_BLOCK, Material.SAND
    );

    private static final Set<Material> BAMBOO_SOILS = EnumSet.of(
            Material.DIRT, Material.GRASS_BLOCK, Material.SAND, Material.MUD
    );

    private static final Set<Material> MUSHROOM_SOILS = EnumSet.of(
            Material.DIRT, Material.GRASS_BLOCK, Material.PODZOL, Material.MYCELIUM
    );

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onDispenserDispense(BlockDispenseEvent event) {
        Block block = event.getBlock();
        // Ensure the block is actually a dispenser
        if (!(block.getState() instanceof Dispenser)) {
            return;
        }

        ItemStack item = event.getItem();
        Material mat = item.getType();

        // Get the facing direction of the dispenser
        BlockFace face = BlockFace.NORTH; // Default, will be overridden
        if (block.getBlockData() instanceof Directional) {
            face = ((Directional) block.getBlockData()).getFacing();
        }

        Block target = block.getRelative(face); // The block directly in front of the dispenser
        Block aboveTarget = target.getRelative(BlockFace.UP); // The block above the target

        boolean planted = false;

        // --- Common check for most plants: requires air above the target block ---
        // Cocoa beans are an exception as they plant on the side.
        if (mat != Material.COCOA_BEANS && aboveTarget.getType() != Material.AIR) {
            // If the space above is not air, we can't plant most items.
            // Let the dispenser do its default action (shoot the item).
            return;
        }

        // --- Planting Logic ---

        // Crop seeds (wheat, beetroot, melon, pumpkin), potatoes, carrots
        if (isCropSeed(mat) || mat == Material.POTATO || mat == Material.CARROT) {
            // If the land is not already ploughed and can be, we plough it
            if (FARMABLE_SOILS.contains(target.getType())) {
                target.setType(Material.FARMLAND);
            }
            // Plant if the soil is tilled (either was already farmland or we just tilled it)
            if (target.getType() == Material.FARMLAND) {
                if (isCropSeed(mat)) {
                    plantBlock(aboveTarget, getCropForSeed(mat));
                } else if (mat == Material.POTATO) {
                    plantBlock(aboveTarget, Material.POTATOES);
                } else if (mat == Material.CARROT) {
                    plantBlock(aboveTarget, Material.CARROTS);
                }
                planted = true;
            }
        }
        // Tree saplings
        else if (isSapling(mat)) {
            // Plant on valid dirt-like blocks
            if (SAPLING_SOILS.contains(target.getType())) {
                plantBlock(aboveTarget, mat);
                planted = true;
            }
        }
        // Sugar cane
        else if (mat == Material.SUGAR_CANE) {
            // Plant on dirt, grass, or sand next to water
            if (SUGAR_CANE_SOILS.contains(target.getType()) && isNearWater(target)) {
                plantBlock(aboveTarget, Material.SUGAR_CANE);
                planted = true;
            }
        }
        // Bamboo
        else if (mat == Material.BAMBOO) {
            // Plant on dirt, grass, sand, or mud
            if (BAMBOO_SOILS.contains(target.getType())) {
                plantBlock(aboveTarget, Material.BAMBOO);
                planted = true;
            }
        }
        // Mushrooms
        else if (mat == Material.BROWN_MUSHROOM || mat == Material.RED_MUSHROOM) {
            // Plant on dirt, grass, podzol, or mycelium
            if (MUSHROOM_SOILS.contains(target.getType())) {
                plantBlock(aboveTarget, mat);
                planted = true;
            }
        }
        // Sweet berries
        else if (mat == Material.SWEET_BERRIES) {
            // Plant on grass block
            if (target.getType() == Material.GRASS_BLOCK) {
                plantBlock(aboveTarget, Material.SWEET_BERRY_BUSH);
                planted = true;
            }
        }
        // Nether wart
        else if (mat == Material.NETHER_WART) {
            // Plant on soul sand
            if (target.getType() == Material.SOUL_SAND) {
                plantBlock(aboveTarget, Material.NETHER_WART);
                planted = true;
            }
        }
        // Chorus flower
        else if (mat == Material.CHORUS_FLOWER) {
            // Plant on end stone
            if (target.getType() == Material.END_STONE) {
                plantBlock(aboveTarget, Material.CHORUS_FLOWER);
                planted = true;
            }
        }
        // Cocoa beans (Special case: plants on the *side* of the log)
        else if (mat == Material.COCOA_BEANS) {
            // Cocoa grows on the side of a Jungle Log.
            // It needs the 'target' block to be a Jungle Log, and the specific face
            // to which it's attaching should be air (or replaceable).
            // We set the target block itself to Cocoa, specifying its facing.
            if (target.getType() == Material.JUNGLE_LOG) {
                // Get the BlockData for Cocoa and set its facing direction
                Directional cocoaData = (Directional) Material.COCOA.createBlockData();
                cocoaData.setFacing(face); // Plant on the face the dispenser is pointing at
                target.setBlockData(cocoaData);
                planted = true;
            }
        }


        // --- Item Consumption Logic ---
        // If an item was successfully planted, prevent it from being shot out
        // and manually remove it from the dispenser's inventory.
        if (planted) {
            event.setCancelled(true); // Stop the default dispense behavior

            Dispenser dispenser = (Dispenser) block.getState();
            Inventory dispenserInventory = dispenser.getInventory();

            // Create an ItemStack representing the item to remove.
            // We want to remove *one* of the original item's type.
            ItemStack itemToConsume = new ItemStack(item.getType(), 1);

            // This method finds and removes items from the inventory.
            // It correctly handles stacks, reducing their amount or removing the stack entirely.
            dispenserInventory.removeItem(itemToConsume);
        }
        // If not planted, the event is not cancelled, and the dispenser will proceed
        // with its default behavior (shooting the item).
    }

    // Helper method to set a block's type
    private void plantBlock(Block block, Material plantMaterial) {
        block.setType(plantMaterial);
    }

    // Returns true if the material is a crop seed (wheat, beetroot, melon, pumpkin)
    private boolean isCropSeed(Material mat) {
        return mat == Material.WHEAT_SEEDS || mat == Material.BEETROOT_SEEDS ||
               mat == Material.MELON_SEEDS || mat == Material.PUMPKIN_SEEDS;
    }

    // Returns true if the material is a tree sapling
    private boolean isSapling(Material mat) {
        return mat == Material.OAK_SAPLING || mat == Material.BIRCH_SAPLING ||
               mat == Material.JUNGLE_SAPLING || mat == Material.SPRUCE_SAPLING ||
               mat == Material.DARK_OAK_SAPLING || mat == Material.ACACIA_SAPLING ||
               mat == Material.CHERRY_SAPLING;
    }

    // Checks if the block is adjacent to water (for sugar cane)
    private boolean isNearWater(Block block) {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            // Only check cardinal directions for water sources
            if (block.getRelative(face).getType() == Material.WATER) {
                return true;
            }
        }
        return false;
    }

    // Returns the crop block type for a given seed
    private Material getCropForSeed(Material seed) {
        switch (seed) {
            case WHEAT_SEEDS: return Material.WHEAT;
            case BEETROOT_SEEDS: return Material.BEETROOTS;
            case MELON_SEEDS: return Material.MELON_STEM;
            case PUMPKIN_SEEDS: return Material.PUMPKIN_STEM;
            default: return Material.AIR; // Should not happen if isCropSeed is true
        }
    }
}