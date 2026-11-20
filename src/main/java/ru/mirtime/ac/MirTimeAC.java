/*
 *    __  __ _        _____ _                
 *   |  \/  (_)_ __  |_   _(_)_ __ ___   ___ 
 *   | |\/| | | '__|   | | | | '_ ` _ \ / _ \
 *   | |  | | | |      | | | | | | | | |  __/
 *   |_|  |_|_|_|      |_| |_|_| |_| |_|\___|
 *                                           
 *   Project: Mir Time | Author: Ars3nBar | MT-AC v3.0 HARDCORE
 */
package ru.mirtime.ac;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MirTimeAC extends JavaPlugin implements CommandExecutor, Listener {

    private final Map<String, Integer> violations = new HashMap<>();
    private final Map<UUID, Integer> totalVl = new HashMap<>();
    private final Map<String, Long> lastReportTime = new HashMap<>();
    private final Map<UUID, Integer> airTicks = new HashMap<>();
    private final Map<UUID, Integer> cpsCounter = new HashMap<>();
    private final Map<UUID, Long> lastHitTime = new HashMap<>();
    private final Map<UUID, UUID> lastHitTarget = new HashMap<>();
    private final Map<UUID, Long> lastBreakTime = new HashMap<>();
    private final Map<UUID, Location> lastSafeLoc = new HashMap<>();
    private final Map<UUID, Integer> packetsPerSec = new HashMap<>();
    private NamespacedKey mtItemKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mtItemKey = new NamespacedKey("mirtimeitems", "mt_item_id");
        if (getCommand("mtac") != null) getCommand("mtac").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);

        // Проверка CPS и Timer каждую секунду
        new BukkitRunnable() {
            @Override
            public void run() {
                int maxCps = getConfig().getInt("checks.autoclicker.max-cps", 18);
                for (Map.Entry<UUID, Integer> entry : cpsCounter.entrySet()) {
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p != null && !isExempt(p) && entry.getValue() > maxCps) {
                        flag(p, "AutoClicker", "CPS: " + entry.getValue(), 2, false);
                    }
                }
                cpsCounter.clear();

                for (Map.Entry<UUID, Integer> entry : packetsPerSec.entrySet()) {
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p != null && !isExempt(p) && entry.getValue() > 26) {
                        flag(p, "Timer", "Пакеты: " + entry.getValue() + "/с", 3, true);
                    }
                }
                packetsPerSec.clear();
            }
        }.runTaskTimer(this, 20L, 20L);

        // Постепенный спад VL каждые 15 секунд
        new BukkitRunnable() {
            @Override
            public void run() {
                violations.replaceAll((k, v) -> Math.max(0, v - 1));
                totalVl.replaceAll((k, v) -> Math.max(0, v - 1));
            }
        }.runTaskTimer(this, 300L, 300L);

        Bukkit.getConsoleSender().sendMessage(color("&#0055ff   __  __ _        _____ _                "));
        Bukkit.getConsoleSender().sendMessage(color("&#0055ff  |  \\/  (_)_ __  |_   _(_)_ __ ___   ___ "));
        Bukkit.getConsoleSender().sendMessage(color("&#00E5FF  | |\\/| | | '__|   | | | | '_ ` _ \\ / _ \\"));
        Bukkit.getConsoleSender().sendMessage(color("&#00E5FF  | |  | | | |      | | | | | | | | |  __/"));
        Bukkit.getConsoleSender().sendMessage(color("&#00FF66  |_|  |_|_|_|      |_| |_|_| |_| |_|\\___|"));
        Bukkit.getConsoleSender().sendMessage(color("&#FF2A55  MT-AC v3.0 HARDCORE (3D HitBox AABB + MirTimePunish Sync) запущен!"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(color(getConfig().getString("prefix", "&#0055ff&l[MT-AC] &8» ") + "&#00FF66Конфиг MT-AC v3.0 перезагружен!"));
            return true;
        }
        sender.sendMessage(color(getConfig().getString("prefix", "&#0055ff&l[MT-AC] &8» ") + "&fMT-AC v3.0 HARDCORE активен. Перезагрузка: &#00FF66/mtac reload"));
        return true;
    }

    private String getMtItem(ItemStack is) {
        if (is == null || !is.hasItemMeta()) return "";
        String val = is.getItemMeta().getPersistentDataContainer().get(mtItemKey, PersistentDataType.STRING);
        return val != null ? val : "";
    }

    private boolean isExempt(Player p) {
        return p.hasPermission("mtac.bypass")
                || p.getGameMode() == GameMode.CREATIVE
                || p.getGameMode() == GameMode.SPECTATOR
                || p.isFlying()
                || p.isGliding()
                || p.isInsideVehicle();
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(PlayerInteractEvent e) {
        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
            UUID id = e.getPlayer().getUniqueId();
            cpsCounter.put(id, cpsCounter.getOrDefault(id, 0) + 1);
        }
    }

    // ЖЁСТКИЙ БОЕВОЙ МОДУЛЬ: 3D HitBox AABB + Reach + MultiAura + Criticals
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity target)) return;
        if (isExempt(p)) return;

        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();

        // 1. MultiAura (удары по разным целям быстрее 60 мс)
        UUID prevTarget = lastHitTarget.get(id);
        long prevTime = lastHitTime.getOrDefault(id, 0L);
        if (prevTarget != null && !prevTarget.equals(target.getUniqueId()) && (now - prevTime) < 60L) {
            e.setCancelled(true);
            flag(p, "MultiAura", "Смена цели за " + (now - prevTime) + "мс", 4, false);
            return;
        }
        lastHitTarget.put(id, target.getUniqueId());
        lastHitTime.put(id, now);

        // Расчёт точного 3D хитбокса с компенсацией пинга
        double pingBonus = Math.min(0.45, p.getPing() * 0.0022);
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        BoundingBox box = target.getBoundingBox().expand(0.28 + pingBonus);

        // 2. Reach (расстояние от глаз до ближайшей точки 3D хитбокса)
        double maxReach = getConfig().getDouble("checks.reach.max-distance", 3.25) + pingBonus;
        if (p.isSprinting()) maxReach += 0.25;

        double closestX = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
        double closestY = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
        double closestZ = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));
        double exactDist = eye.toVector().distance(new Vector(closestX, closestY, closestZ));

        if (exactDist > maxReach) {
            e.setCancelled(true);
            flag(p, "Reach", String.format(Locale.US, "%.2f / %.2f бл.", exactDist, maxReach), 3, false);
            return;
        }

        // 3. HitBox / KillAura (проверка пересечения луча взгляда с 3D коробкой цели)
        if (exactDist > 0.9 && box.rayTrace(eye.toVector(), dir, maxReach + 0.6) == null) {
            Vector toCenter = target.getBoundingBox().getCenter().subtract(eye.toVector()).normalize();
            double angle = Math.toDegrees(dir.angle(toCenter));
            e.setCancelled(true);
            flag(p, "HitBox/KillAura", String.format(Locale.US, "Мимо хитбокса (угол %.0f°)", angle), 3, false);
            return;
        }

        // 4. Packet Criticals (крит без падения и не в воде/на лестнице)
        if (p.getFallDistance() > 0.0f && p.getFallDistance() < 0.08f && !p.isInWater() && !p.isClimbing()) {
            Block under = p.getLocation().subtract(0, 0.1, 0).getBlock();
            if (!under.getType().isAir()) {
                e.setCancelled(true);
                flag(p, "Criticals", "Пакетный крит на земле", 2, false);
            }
        }
    }

    // ЖЁСТКИЙ МОДУЛЬ ДВИЖЕНИЯ + ОТКАТ (Setback) + ЗАЩИТА MirTimeItems
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        packetsPerSec.put(id, packetsPerSec.getOrDefault(id, 0) + 1);

        if (isExempt(p)) {
            airTicks.remove(id);
            lastSafeLoc.put(id, e.getTo());
            return;
        }

        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || from.getWorld() != to.getWorld()) return;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dy = to.getY() - from.getY();
        double horiz = Math.hypot(dx, dz);

        Block below = to.clone().subtract(0, 0.3, 0).getBlock();
        boolean inAir = below.getType().isAir() && !p.isClimbing() && !p.isInWater() && !p.isInLava();
        if (!inAir && dy == 0) lastSafeLoc.put(id, from.clone());

        // 1. Fly / Hover
        if (!p.hasPotionEffect(PotionEffectType.LEVITATION) && !p.hasPotionEffect(PotionEffectType.SLOW_FALLING) && p.getNoDamageTicks() <= 0) {
            if (inAir && dy >= -0.04) {
                int ticks = airTicks.getOrDefault(id, 0) + 1;
                airTicks.put(id, ticks);
                if (ticks >= getConfig().getInt("checks.fly.max-air-ticks", 22)) {
                    flag(p, "Fly", "Зависание в воздухе (" + ticks + " тиков)", 3, true);
                    airTicks.put(id, 0);
                }
            } else {
                airTicks.put(id, 0);
            }
        }

        // 2. SpeedHack (с учётом Ботинок Гермеса и Талисмана Скорости из MirTimeItems)
        if (horiz > 0.12 && p.getNoDamageTicks() <= 0) {
            double maxSpd = getConfig().getDouble("checks.speed.max-horizontal-speed", 0.76);
            if (p.hasPotionEffect(PotionEffectType.SPEED)) {
                maxSpd += (p.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1) * 0.18;
            }
            if ("hermes_boots".equals(getMtItem(p.getInventory().getBoots()))) maxSpd += 0.25;
            if ("talisman_speed".equals(getMtItem(p.getInventory().getItemInOffHand()))) maxSpd += 0.25;
            if (below.getType().name().contains("ICE") || below.getType().name().contains("SLIME")) maxSpd += 0.38;

            if (horiz > maxSpd && dy >= -0.35) {
                flag(p, "SpeedHack", String.format(Locale.US, "Скорость %.2f (макс %.2f)", horiz, maxSpd), 2, true);
            }
        }

        // 3. Jesus (ходьба по воде/лаве)
        Block feet = to.getBlock();
        Block under = to.clone().subtract(0, 0.15, 0).getBlock();
        if (feet.getType().isAir() && (under.getType() == Material.WATER || under.getType() == Material.LAVA) && horiz > 0.29 && Math.abs(dy) < 0.02) {
            flag(p, "Jesus", "Ходьба по поверхности жидкости", 3, true);
        }
    }
    // InventoryMove + FastBreak + Scaffold + NoFall
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && !isExempt(p)) {
            if (p.isSprinting() || p.getVelocity().lengthSquared() > 0.08) {
                e.setCancelled(true);
                flag(p, "InventoryMove", "Клик в инвентаре на бегу", 2, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (isExempt(p) || p.hasPotionEffect(PotionEffectType.HASTE)) return;
        String toolId = getMtItem(p.getInventory().getItemInMainHand());
        if (toolId.equals("drill_pickaxe") || toolId.equals("magnet_pickaxe") || toolId.equals("farmer_hoe")) return;

        Material m = e.getBlock().getType();
        if (m == Material.OBSIDIAN || m == Material.ANCIENT_DEBRIS) {
            long now = System.currentTimeMillis();
            long diff = now - lastBreakTime.getOrDefault(p.getUniqueId(), 0L);
            lastBreakTime.put(p.getUniqueId(), now);
            if (diff < 850L) {
                e.setCancelled(true);
                flag(p, "FastBreak", "Добыча " + m.name() + " за " + diff + "мс", 4, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (isExempt(p)) return;
        Block placed = e.getBlockPlaced();
        if (placed.getY() < p.getLocation().getY() && p.isSprinting() && p.getLocation().getPitch() < 45.0f) {
            e.setCancelled(true);
            flag(p, "Scaffold", "Застройка под себя без взгляда вниз", 3, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (isExempt(p) || "hermes_boots".equals(getMtItem(p.getInventory().getBoots()))) return;
            if (p.getFallDistance() > 5.5 && e.getFinalDamage() <= 0.1) {
                flag(p, "NoFall", "Падение с " + (int) p.getFallDistance() + "м без урона", 3, false);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        airTicks.remove(id);
        cpsCounter.remove(id);
        lastSafeLoc.remove(id);
        packetsPerSec.remove(id);
    }

    // СИНХРОНИЗАЦИЯ С MirTimePunish (/acreport + АВТО-БАН)
    private void flag(Player p, String checkName, String details, int weight, boolean setback) {
        UUID id = p.getUniqueId();
        if (setback && lastSafeLoc.containsKey(id)) {
            p.teleport(lastSafeLoc.get(id));
        }

        String key = id + ":" + checkName;
        int vl = violations.getOrDefault(key, 0) + weight;
        violations.put(key, vl);

        int sumVl = totalVl.getOrDefault(id, 0) + weight;
        totalVl.put(id, sumVl);

        // Авто-бан через MirTimePunish при грубом читерстве (sumVl >= 16)
        int autoBanVl = getConfig().getInt("auto-ban-vl", 16);
        if (sumVl >= autoBanVl) {
            totalVl.remove(id);
            violations.remove(key);
            String banCmd = getConfig().getString("ban-command", "ban %player% 14d [MT-AC] Использование читов (%check%)")
                    .replace("%player%", p.getName())
                    .replace("%check%", checkName);
            Bukkit.broadcastMessage(color(getConfig().getString("prefix", "&#0055ff&l[MT-AC] &8» ") + "&#FF2A55&lИгрок &f" + p.getName() + " &#FF2A55&lзаблокирован античитом за &e" + checkName + "&f!"));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), banCmd);
            return;
        }

        int needed = getConfig().getInt("vl-to-report", 4);
        if (vl < needed) return;
        violations.put(key, 0);

        long now = System.currentTimeMillis();
        if (now - lastReportTime.getOrDefault(key, 0L) < 10_000L) return;
        lastReportTime.put(key, now);

        // Передача репорта в MirTimePunish
        if (Bukkit.getPluginManager().isPluginEnabled("MirTimePunish")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "acreport " + p.getName() + " " + checkName + " (" + details + ")");
        }

        // Интерактивный алерт модераторам в чат
        String prefix = getConfig().getString("prefix", "&#0055ff&l[MT-AC] &8» ");
        TextComponent msg = new TextComponent(color(prefix + "&c" + p.getName() + " &fподозревается в &#00E5FF" + checkName + " &8(&7" + details + " &8| &eVL: " + sumVl + "&8) "));
        TextComponent btnTp = new TextComponent(color("&#00FF66&l[СЛЕДИТЬ] "));
        btnTp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + p.getName()));
        TextComponent btnCheck = new TextComponent(color("&#FF2A55&l[ПРОВЕРКА]"));
        btnCheck.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/check " + p.getName()));

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("mtac.alerts")) {
                staff.spigot().sendMessage(msg, btnTp, btnCheck);
                staff.playSound(staff.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
            }
        }
    }

    public static String color(String text) {
        if (text == null) return "";
        Pattern pattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder rep = new StringBuilder("§x");
            for (char c : hex.toCharArray()) rep.append('§').append(c);
            matcher.appendReplacement(buffer, rep.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
