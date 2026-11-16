/*
 *    __  __ _        _____ _                
 *   |  \/  (_)_ __  |_   _(_)_ __ ___   ___ 
 *   | |\/| | | '__|   | | | | '_ ` _ \ / _ \
 *   | |  | | | |      | | | | | | | | |  __/
 *   |_|  |_|_|_|      |_| |_|_| |_| |_|\___|
 *                                           
 *   Project: Mir Time | Author: Ars3nBar
 */
package ru.mirtime.ac;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MirTimeAC extends JavaPlugin implements CommandExecutor, Listener {

    private final Map<String, Integer> violations = new HashMap<>();
    private final Map<String, Long> lastReportTime = new HashMap<>();
    private final Map<UUID, Integer> airTicks = new HashMap<>();
    private final Map<UUID, Integer> cpsCounter = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (getCommand("mtac") != null) getCommand("mtac").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                int maxCps = getConfig().getInt("checks.autoclicker.max-cps", 19);
                boolean enabled = getConfig().getBoolean("checks.autoclicker.enabled", true);
                for (Map.Entry<UUID, Integer> entry : cpsCounter.entrySet()) {
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p != null && enabled && !isExempt(p) && entry.getValue() > maxCps) {
                        flag(p, "AutoClicker", "CPS: " + entry.getValue(), "checks.autoclicker.vl-to-report");
                    }
                }
                cpsCounter.clear();
            }
        }.runTaskTimer(this, 20L, 20L);

        new BukkitRunnable() {
            @Override
            public void run() {
                violations.replaceAll((k, v) -> Math.max(0, v - 1));
            }
        }.runTaskTimer(this, 200L, 200L);

        Bukkit.getConsoleSender().sendMessage(color("&#0055ff   __  __ _        _____ _                "));
        Bukkit.getConsoleSender().sendMessage(color("&#0055ff  |  \\/  (_)_ __  |_   _(_)_ __ ___   ___ "));
        Bukkit.getConsoleSender().sendMessage(color("&#00E5FF  | |\\/| | | '__|   | | | | '_ ` _ \\ / _ \\"));
        Bukkit.getConsoleSender().sendMessage(color("&#00E5FF  | |  | | | |      | | | | | | | | |  __/"));
        Bukkit.getConsoleSender().sendMessage(color("&#00FF66  |_|  |_|_|_|      |_| |_|_| |_| |_|\\___|"));
        Bukkit.getConsoleSender().sendMessage(color("&#00E5FF  MT-AC v1.0.0 успешно запущен!"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && List.of(args).get(0).equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(color(getConfig().getString("prefix") + "&#00FF66Конфиг MT-AC успешно перезагружен!"));
            return true;
        }
        sender.sendMessage(color(getConfig().getString("prefix") + "&fАнтичит активен. Перезагрузка: &#00FF66/mtac reload"));
        return true;
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (isExempt(p)) return;

        Location eye = p.getEyeLocation();
        Location targetLoc = e.getEntity().getLocation().add(0, e.getEntity().getHeight() / 2.0, 0);
        double dist = eye.distance(targetLoc);

        if (getConfig().getBoolean("checks.reach.enabled", true)) {
            double maxDist = getConfig().getDouble("checks.reach.max-distance", 3.9);
            if (p.isSprinting()) maxDist += 0.35;
            if (dist > maxDist) {
                flag(p, "Reach", String.format(Locale.US, "%.2f блоков", dist), "checks.reach.vl-to-report");
            }
        }

        if (getConfig().getBoolean("checks.killaura.enabled", true) && dist > 1.3) {
            Vector toTarget = targetLoc.toVector().subtract(eye.toVector()).normalize();
            Vector look = eye.getDirection().normalize();
            double angle = Math.toDegrees(look.angle(toTarget));
            double maxAngle = getConfig().getDouble("checks.killaura.max-angle", 115.0);
            if (angle > maxAngle) {
                flag(p, "KillAura", String.format(Locale.US, "Угол %.0f°", angle), "checks.killaura.vl-to-report");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (isExempt(p)) {
            airTicks.remove(p.getUniqueId());
            return;
        }

        Location from = e.getFrom();
        Location to = e.getTo();
        if (from.getWorld() != to.getWorld()) return;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dy = to.getY() - from.getY();
        double horiz = Math.hypot(dx, dz);

        Block below = to.clone().subtract(0, 0.3, 0).getBlock();
        boolean inAir = below.getType().isAir() && !p.isClimbing() && !p.isInWater() && !p.isInLava();

        if (getConfig().getBoolean("checks.fly.enabled", true)) {
            if (inAir && dy >= -0.05 && !p.hasPotionEffect(PotionEffectType.LEVITATION) && !p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
                int ticks = airTicks.getOrDefault(p.getUniqueId(), 0) + 1;
                airTicks.put(p.getUniqueId(), ticks);
                if (ticks >= getConfig().getInt("checks.fly.max-air-ticks", 24)) {
                    flag(p, "Fly", "В воздухе " + ticks + " тиков", "checks.fly.vl-to-report");
                    airTicks.put(p.getUniqueId(), 0);
                }
            } else {
                airTicks.put(p.getUniqueId(), 0);
            }
        }

        if (getConfig().getBoolean("checks.speed.enabled", true) && horiz > 0.1) {
            double maxSpd = getConfig().getDouble("checks.speed.max-horizontal-speed", 0.78);
            if (p.hasPotionEffect(PotionEffectType.SPEED)) {
                int amp = p.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1;
                maxSpd += amp * 0.16;
            }
            if (below.getType().name().contains("ICE") || below.getType().name().contains("SLIME")) maxSpd += 0.35;
            if (p.getNoDamageTicks() > 0) maxSpd += 0.45;

            if (horiz > maxSpd && dy >= -0.4) {
                flag(p, "SpeedHack", String.format(Locale.US, "Скорость %.2f", horiz), "checks.speed.vl-to-report");
            }
        }

        if (getConfig().getBoolean("checks.jesus.enabled", true)) {
            Block feet = to.getBlock();
            Block under = to.clone().subtract(0, 0.15, 0).getBlock();
            if (feet.getType().isAir() && (under.getType() == Material.WATER || under.getType() == Material.LAVA) && horiz > 0.28 && Math.abs(dy) < 0.02) {
                flag(p, "Jesus", "Ходьба по жидкости", "checks.jesus.vl-to-report");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (!isExempt(p) && getConfig().getBoolean("checks.nofall.enabled", true) && p.getFallDistance() > 5.0 && e.getFinalDamage() <= 0.1) {
                flag(p, "NoFall", "Падение без урона", "checks.nofall.vl-to-report");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        airTicks.remove(id);
        cpsCounter.remove(id);
    }

    private void flag(Player p, String checkName, String details, String thresholdPath) {
        String key = p.getUniqueId() + ":" + checkName;
        int vl = violations.getOrDefault(key, 0) + 1;
        violations.put(key, vl);

        int needed = getConfig().getInt(thresholdPath, 4);
        if (vl < needed) return;
        violations.put(key, 0);

        long now = System.currentTimeMillis();
        if (now - lastReportTime.getOrDefault(key, 0L) < 15_000L) return;
        lastReportTime.put(key, now);

        boolean linkPunish = getConfig().getBoolean("link-mirtime-punish", true);
        if (linkPunish && Bukkit.getPluginManager().isPluginEnabled("MirTimePunish")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "acreport " + p.getName() + " " + checkName + " (" + details + ")");
        } else {
            String prefix = getConfig().getString("prefix", "&#0055ff&l[MT-AC] &8» ");
            TextComponent msg = new TextComponent(color(prefix + "&c" + p.getName() + " &fподозревается в &#00E5FF" + checkName + " &8(&7" + details + "&8) "));
            TextComponent btn = new TextComponent(color("&#00FF66&l[СЛЕДИТЬ]"));
            btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + p.getName()));
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("mtac.alerts")) {
                    staff.spigot().sendMessage(msg, btn);
                    staff.playSound(staff.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
                }
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
