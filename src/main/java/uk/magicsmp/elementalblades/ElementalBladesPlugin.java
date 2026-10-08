package uk.magicsmp.elementalblades;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public final class ElementalBladesPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private NamespacedKey fireKey;
    private NamespacedKey iceKey;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    private static final long FIRE_BURST_CD = 8_000L;
    private static final long FLAME_DASH_CD = 6_000L;
    private static final long INFERNO_STRIKE_CD = 20_000L;
    private static final long ICE_NOVA_CD = 8_000L;
    private static final long FROST_DASH_CD = 6_000L;
    private static final long GLACIAL_STRIKE_CD = 20_000L;
    private static final String BLOCKED_WORLD = "spawn";

    @Override
    public void onEnable() {
        fireKey = new NamespacedKey(this, "inferno_blade");
        iceKey = new NamespacedKey(this, "frostbite_blade");
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("elementalblade")).setExecutor(this);
        getLogger().info("ElementalBlades enabled. Elemental abilities are disabled in world '" + BLOCKED_WORLD + "'.");
    }

    @Override
    public void onDisable() {
        cooldowns.clear();
    }

    private boolean hasMarker(ItemStack item, NamespacedKey key) {
        if (item == null || item.getType() != Material.NETHERITE_SWORD || !item.hasItemMeta()) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private boolean isFire(ItemStack item) { return hasMarker(item, fireKey); }
    private boolean isIce(ItemStack item) { return hasMarker(item, iceKey); }
    private boolean isElemental(ItemStack item) { return isFire(item) || isIce(item); }

    private ItemStack createBlade(boolean fire) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(fire
                ? ChatColor.GOLD.toString() + ChatColor.BOLD + "Inferno Blade"
                : ChatColor.AQUA.toString() + ChatColor.BOLD + "Frostbite Blade");
        meta.setLore(fire ? List.of(
                ChatColor.GRAY + "A weapon forged in eternal flame.", "",
                ChatColor.GOLD + "Right-click" + ChatColor.GRAY + "  Fire Burst",
                ChatColor.GOLD + "Sneak + right-click" + ChatColor.GRAY + "  Inferno Strike",
                ChatColor.GOLD + "Swap hands (F)" + ChatColor.GRAY + "  Flame Dash",
                "", ChatColor.DARK_GRAY + "MagicSMP Legendary Weapon"
        ) : List.of(
                ChatColor.GRAY + "A blade chilled by ancient magic.", "",
                ChatColor.AQUA + "Right-click" + ChatColor.GRAY + "  Ice Nova",
                ChatColor.AQUA + "Sneak + right-click" + ChatColor.GRAY + "  Glacial Strike",
                ChatColor.AQUA + "Swap hands (F)" + ChatColor.GRAY + "  Frost Dash",
                "", ChatColor.DARK_GRAY + "MagicSMP Legendary Weapon"
        ));
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        if (fire) {
            meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
            meta.getPersistentDataContainer().set(fireKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
            meta.getPersistentDataContainer().set(iceKey, PersistentDataType.BYTE, (byte) 1);
        }
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(ChatColor.RED + "Usage: /elementalblade give <fire|ice> <player>");
            return true;
        }
        boolean fire;
        if (args[1].equalsIgnoreCase("fire")) fire = true;
        else if (args[1].equalsIgnoreCase("ice")) fire = false;
        else {
            sender.sendMessage(ChatColor.RED + "Choose fire or ice.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "That player is not online.");
            return true;
        }
        ItemStack blade = createBlade(fire);
        HashMap<Integer, ItemStack> leftovers = target.getInventory().addItem(blade);
        leftovers.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        String name = fire ? "Inferno Blade" : "Frostbite Blade";
        sender.sendMessage(ChatColor.GREEN + "Gave " + name + " to " + target.getName() + ".");
        target.sendMessage((fire ? ChatColor.GOLD : ChatColor.AQUA) + "You received the " + name + "!");
        return true;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!isElemental(item)) return;
        event.setCancelled(true);

        if (isBlockedWorld(player)) {
            player.sendMessage(ChatColor.RED + "Elemental swords cannot be used in the spawn world.");
            return;
        }
        if (!player.hasPermission("elementalblades.use")) {
            player.sendMessage(ChatColor.RED + "You cannot use this weapon's abilities.");
            return;
        }
        boolean fire = isFire(item);
        if (player.isSneaking()) {
            if (fire) infernoStrike(player); else glacialStrike(player);
        } else {
            if (fire) fireBurst(player); else iceNova(player);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack held = event.getMainHandItem();
        if (!isElemental(held)) return;
        event.setCancelled(true);
        if (isBlockedWorld(player)) {
            player.sendMessage(ChatColor.RED + "Elemental swords cannot be used in the spawn world.");
            return;
        }
        if (!player.hasPermission("elementalblades.use")) return;
        if (isFire(held)) flameDash(player); else frostDash(player);
    }

    @EventHandler
    public void onBladeHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isElemental(held)) return;
        if (isBlockedWorld(player)) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target) || target == player) return;
        if (target instanceof Player && !player.getWorld().getPVP()) return;
        if (isFire(held)) {
            target.setFireTicks(Math.max(target.getFireTicks(), 80));
        } else {
            target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), target.getFreezeTicks() + 50));
            target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOWNESS, 50, 1, false, true, true));
        }
    }

    private boolean isBlockedWorld(Player player) {
        return player.getWorld().getName().equalsIgnoreCase(BLOCKED_WORLD);
    }

    private boolean ready(Player player, String ability, long cooldownMs) {
        long now = System.currentTimeMillis();
        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        long next = playerCooldowns.getOrDefault(ability, 0L);
        if (now < next) {
            long seconds = (long) Math.ceil((next - now) / 1000.0);
            player.sendMessage(ChatColor.RED + ability + " is on cooldown for " + seconds + "s.");
            return false;
        }
        playerCooldowns.put(ability, now + cooldownMs);
        return true;
    }

    private boolean validTarget(LivingEntity target, Player player) {
        if (target == player) return false;
        if (target instanceof Player && !player.getWorld().getPVP()) return false;
        return true;
    }

    private void fireBurst(Player player) {
        if (!ready(player, "Fire Burst", FIRE_BURST_CD)) return;
        Location center = player.getLocation();
        World world = player.getWorld();
        world.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.8f);
        world.spawnParticle(Particle.FLAME, center.clone().add(0, 1, 0), 100, 2.2, 1.0, 2.2, 0.04);
        world.spawnParticle(Particle.LAVA, center.clone().add(0, 1, 0), 18, 1.6, 0.7, 1.6, 0.02);
        for (Entity entity : world.getNearbyEntities(center, 4, 2.5, 4)) {
            if (!(entity instanceof LivingEntity target) || !validTarget(target, player)) continue;
            if (target.getLocation().distanceSquared(center) > 16) continue;
            target.damage(5.0, player);
            target.setFireTicks(Math.max(target.getFireTicks(), 100));
            Vector push = target.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.55);
            push.setY(0.25);
            target.setVelocity(target.getVelocity().add(push));
        }
        player.sendMessage(ChatColor.GOLD + "Fire Burst unleashed!");
    }

    private void flameDash(Player player) {
        if (!ready(player, "Flame Dash", FLAME_DASH_CD)) return;
        dash(player, true);
        player.sendMessage(ChatColor.GOLD + "Flame Dash!");
    }

    private void frostDash(Player player) {
        if (!ready(player, "Frost Dash", FROST_DASH_CD)) return;
        dash(player, false);
        player.sendMessage(ChatColor.AQUA + "Frost Dash!");
    }

    private void dash(Player player, boolean fire) {
        Vector direction = player.getLocation().getDirection().normalize();
        direction.setY(Math.max(-0.05, direction.getY()));
        player.setVelocity(direction.multiply(1.45).setY(Math.max(0.18, direction.getY() * 0.75)));
        player.getWorld().playSound(player.getLocation(),
                fire ? Sound.ENTITY_BLAZE_SHOOT : Sound.BLOCK_GLASS_BREAK, 1.0f, fire ? 1.15f : 1.65f);
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!player.isOnline() || isBlockedWorld(player) || ticks++ >= 8) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation();
                World world = player.getWorld();
                world.spawnParticle(fire ? Particle.FLAME : Particle.SNOWFLAKE, loc,
                        24, 0.45, 0.45, 0.45, 0.025);
                world.spawnParticle(fire ? Particle.SMOKE : Particle.CLOUD, loc,
                        8, 0.3, 0.25, 0.3, 0.01);
                for (Entity entity : world.getNearbyEntities(loc, 1.15, 1.0, 1.15)) {
                    if (!(entity instanceof LivingEntity target) || !validTarget(target, player)
                            || hit.contains(target.getUniqueId())) continue;
                    hit.add(target.getUniqueId());
                    target.damage(4.0, player);
                    applyElement(target, fire);
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void applyElement(LivingEntity target, boolean fire) {
        if (fire) {
            target.setFireTicks(Math.max(target.getFireTicks(), 80));
        } else {
            target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), target.getFreezeTicks() + 50));
            target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOWNESS, 50, 1, false, true, true));
        }
    }

    private void infernoStrike(Player player) {
        if (!ready(player, "Inferno Strike", INFERNO_STRIKE_CD)) return;
        Location impact = getImpact(player, 20, 12);
        World world = player.getWorld();
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
        world.playSound(impact, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.65f);
        world.spawnParticle(Particle.EXPLOSION, impact, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLAME, impact, 140, 2.4, 1, 2.4, 0.06);
        world.spawnParticle(Particle.LAVA, impact, 30, 1.8, 0.7, 1.8, 0.03);
        world.spawnParticle(Particle.SMOKE, impact, 55, 1.6, 0.8, 1.6, 0.03);
        for (Entity entity : world.getNearbyEntities(impact, 4, 3, 4)) {
            if (!(entity instanceof LivingEntity target) || !validTarget(target, player)) continue;
            if (target.getLocation().distanceSquared(impact) > 16) continue;
            target.damage(8.0, player);
            target.setFireTicks(Math.max(target.getFireTicks(), 140));
            Vector push = target.getLocation().toVector().subtract(impact.toVector()).normalize().multiply(0.75);
            push.setY(0.35);
            target.setVelocity(target.getVelocity().add(push));
        }
        player.sendMessage(ChatColor.GOLD + "INFERNO STRIKE!");
    }

    private void iceNova(Player player) {
        if (!ready(player, "Ice Nova", ICE_NOVA_CD)) return;
        Location center = player.getLocation();
        World world = player.getWorld();
        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.1f, 0.65f);
        world.spawnParticle(Particle.SNOWFLAKE, center.clone().add(0, 1, 0), 110, 2.2, 1, 2.2, 0.02);
        world.spawnParticle(Particle.CLOUD, center.clone().add(0, 1, 0), 45, 1.8, 0.7, 1.8, 0.02);
        for (Entity entity : world.getNearbyEntities(center, 4, 2.5, 4)) {
            if (!(entity instanceof LivingEntity target) || !validTarget(target, player)) continue;
            if (target.getLocation().distanceSquared(center) > 16) continue;
            target.damage(5.0, player);
            applyElement(target, false);
            Vector push = target.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.35);
            push.setY(0.15);
            target.setVelocity(target.getVelocity().add(push));
        }
        player.sendMessage(ChatColor.AQUA + "Ice Nova unleashed!");
    }

    private void glacialStrike(Player player) {
        if (!ready(player, "Glacial Strike", GLACIAL_STRIKE_CD)) return;
        Location impact = getImpact(player, 20, 12);
        World world = player.getWorld();
        world.playSound(impact, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.45f);
        world.spawnParticle(Particle.SNOWFLAKE, impact, 150, 2.4, 1, 2.4, 0.025);
        world.spawnParticle(Particle.CLOUD, impact, 65, 1.7, 0.8, 1.7, 0.02);
        world.spawnParticle(Particle.END_ROD, impact, 25, 1.2, 0.7, 1.2, 0.03);
        for (Entity entity : world.getNearbyEntities(impact, 4, 3, 4)) {
            if (!(entity instanceof LivingEntity target) || !validTarget(target, player)) continue;
            if (target.getLocation().distanceSquared(impact) > 16) continue;
            target.damage(8.0, player);
            applyElement(target, false);
            target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOWNESS, 100, 2, false, true, true));
        }
        player.sendMessage(ChatColor.AQUA + "GLACIAL STRIKE!");
    }

    private Location getImpact(Player player, double maxDistance, double fallbackDistance) {
        RayTraceResult trace = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), maxDistance,
                FluidCollisionMode.NEVER, true);
        Location impact = trace != null && trace.getHitPosition() != null
                ? trace.getHitPosition().toLocation(player.getWorld())
                : player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(fallbackDistance));
        return impact.add(0, 0.2, 0);
    }
}
