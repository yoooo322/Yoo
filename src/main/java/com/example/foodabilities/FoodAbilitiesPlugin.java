package com.example.foodabilities;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.*;

public class FoodAbilitiesPlugin extends JavaPlugin implements Listener {

    private NamespacedKey recipeKey;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        this.recipeKey = new NamespacedKey(this, "infused_food");
        registerDynamicRecipeInfo();
        getLogger().info("FoodAbilities enabled");
    }

    private void registerDynamicRecipeInfo() {
        // Register a broad shapeless recipe that matches: any potion + any edible
        // We then override the result dynamically in PrepareItemCraftEvent
        try {
            Bukkit.removeRecipe(this.recipeKey);
        } catch (Throwable ignored) {}

        ItemStack placeholder = new ItemStack(Material.BREAD);
        ItemMeta meta = placeholder.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("Infused Food");
            placeholder.setItemMeta(meta);
        }

        ShapelessRecipe dynamic = new ShapelessRecipe(this.recipeKey, placeholder);
        dynamic.addIngredient(new RecipeChoice.MaterialChoice(getPotionMaterials()));
        dynamic.addIngredient(new RecipeChoice.MaterialChoice(getEdibleMaterials()));
        Bukkit.addRecipe(dynamic);
    }

    private List<Material> getPotionMaterials() {
        return Arrays.asList(Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION);
    }

    private List<Material> getEdibleMaterials() {
        List<Material> edibles = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isEdible()) {
                edibles.add(m);
            }
        }
        return edibles;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        Recipe recipe = inv.getRecipe();
        if (!(recipe instanceof ShapelessRecipe)) {
            return;
        }
        ShapelessRecipe shapeless = (ShapelessRecipe) recipe;
        if (!this.recipeKey.equals(shapeless.getKey())) {
            return;
        }

        ItemStack[] matrix = inv.getMatrix();
        ItemStack potion = null;
        ItemStack food = null;
        // Find one potion and one edible food
        for (ItemStack stack : matrix) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            if (isPotion(stack) && potion == null) {
                potion = stack;
            } else if (isEdible(stack) && food == null) {
                food = stack;
            }
        }

        if (potion == null || food == null) {
            inv.setResult(new ItemStack(Material.AIR));
            return;
        }

        ItemStack result = createInfusedFood(food, potion);
        inv.setResult(result);
    }

    private boolean isPotion(ItemStack stack) {
        Material type = stack.getType();
        return type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION;
    }

    private boolean isEdible(ItemStack stack) {
        Material type = stack.getType();
        return type.isEdible();
    }

    private ItemStack createInfusedFood(ItemStack food, ItemStack potion)
    {
        ItemStack result = new ItemStack(food.getType(), 1);
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        String potionName = "Potion";
        PotionType potionType = getPotionType(potion);
        if (potionType != null) {
            potionName = potionType.name();
        }

        meta.setDisplayName("Infused " + toTitle(food.getType().name()) + " (" + potionName + ")");
        List<String> lore = new ArrayList<>();
        lore.add("Right-click or eat to gain infinite effects");
        lore.add("Infused with: " + potionName);
        meta.setLore(lore);
        meta.addEnchant(Enchantment.LUCK, 1, true);
        meta.setCustomModelData(90210); // marker

        result.setItemMeta(meta);

        // Store effect in persistent data via lore marker key in display name (simple)
        // Note: For simplicity, we encode via ItemMeta persistent tags in namespaced key
        ItemMeta im = result.getItemMeta();
        if (im != null) {
            im.getPersistentDataContainer().set(new NamespacedKey(this, "effect"), org.bukkit.persistence.PersistentDataType.STRING, potionName);
            result.setItemMeta(im);
        }
        return result;
    }

    private PotionType getPotionType(ItemStack potion) {
        if (!(potion.getItemMeta() instanceof PotionMeta)) return null;
        PotionMeta pm = (PotionMeta) potion.getItemMeta();
        if (pm.getBasePotionData() != null) {
            return pm.getBasePotionData().getType();
        }
        return null;
    }

    private String toTitle(String name) {
        String lower = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = lower.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null) return;
        if (!isInfusedFood(item)) return;

        Player player = event.getPlayer();
        applyEffects(player, item);
        // simulate consumption by reducing stack if player is not in creative
        if (!player.getGameMode().name().equalsIgnoreCase("CREATIVE")) {
            ItemStack hand = event.getItem();
            if (hand != null) {
                int amt = hand.getAmount();
                if (amt <= 1) {
                    player.getInventory().setItem(event.getHand(), null);
                } else {
                    hand.setAmount(amt - 1);
                }
            }
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!isInfusedFood(item)) return;
        Player player = event.getPlayer();
        applyEffects(player, item);
        // Let normal food consumption proceed; effect application is extra
    }

    private boolean isInfusedFood(ItemStack stack) {
        if (stack == null) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        if (!meta.hasCustomModelData() || meta.getCustomModelData() != 90210) return false;
        return true;
    }

    private void applyEffects(Player player, ItemStack infusedFood) {
        ItemMeta meta = infusedFood.getItemMeta();
        if (meta == null) return;
        String potionName = meta.getPersistentDataContainer().get(new NamespacedKey(this, "effect"), org.bukkit.persistence.PersistentDataType.STRING);
        if (potionName == null) return;

        // Map potion types to effect sets and strengths (speed 10, fire resistance, etc.)
        // Infinite effects approximated by very long duration and refresh on join.
        List<PotionEffect> effects = mapPotionToEffects(potionName);
        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }
    }

    private List<PotionEffect> mapPotionToEffects(String potionName) {
        int infiniteTicks = 1_000_000_000; // ~13 days of in-game time
        int amplifierMax = 9; // level 10 (0-indexed)
        List<PotionEffect> list = new ArrayList<>();
        PotionType type;
        try {
            type = PotionType.valueOf(potionName);
        } catch (IllegalArgumentException e) {
            type = null;
        }
        if (type == null) {
            // Default: give speed, fire resistance as requested
            list.add(new PotionEffect(PotionEffectType.SPEED, infiniteTicks, amplifierMax, true, false, true));
            list.add(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, infiniteTicks, 0, true, false, true));
            return list;
        }

        switch (type) {
            case SPEED:
                list.add(new PotionEffect(PotionEffectType.SPEED, infiniteTicks, amplifierMax, true, false, true));
                break;
            case FIRE_RESISTANCE:
                list.add(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, infiniteTicks, 0, true, false, true));
                break;
            case STRENGTH:
                list.add(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, infiniteTicks, amplifierMax, true, false, true));
                break;
            case INSTANT_HEAL:
                list.add(new PotionEffect(PotionEffectType.HEALTH_BOOST, infiniteTicks, amplifierMax, true, false, true));
                list.add(new PotionEffect(PotionEffectType.REGENERATION, infiniteTicks, amplifierMax, true, false, true));
                break;
            case JUMP:
                list.add(new PotionEffect(PotionEffectType.JUMP, infiniteTicks, amplifierMax, true, false, true));
                break;
            case NIGHT_VISION:
                list.add(new PotionEffect(PotionEffectType.NIGHT_VISION, infiniteTicks, 0, true, false, true));
                break;
            case WATER_BREATHING:
                list.add(new PotionEffect(PotionEffectType.WATER_BREATHING, infiniteTicks, 0, true, false, true));
                break;
            case INVISIBILITY:
                list.add(new PotionEffect(PotionEffectType.INVISIBILITY, infiniteTicks, 0, true, false, true));
                break;
            case REGEN:
                list.add(new PotionEffect(PotionEffectType.REGENERATION, infiniteTicks, amplifierMax, true, false, true));
                break;
            case SLOW_FALLING:
                list.add(new PotionEffect(PotionEffectType.SLOW_FALLING, infiniteTicks, 0, true, false, true));
                break;
            case LUCK:
                list.add(new PotionEffect(PotionEffectType.LUCK, infiniteTicks, amplifierMax, true, false, true));
                break;
            case TURTLE_MASTER:
                list.add(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, infiniteTicks, amplifierMax, true, false, true));
                list.add(new PotionEffect(PotionEffectType.SLOW, infiniteTicks, amplifierMax, true, false, true));
                break;
            default:
                // Fallback
                list.add(new PotionEffect(PotionEffectType.SPEED, infiniteTicks, amplifierMax, true, false, true));
                list.add(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, infiniteTicks, 0, true, false, true));
        }
        return list;
    }
}
