package com.troy.ds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class DeathSwap extends JavaPlugin implements CommandExecutor, Listener
{
	private int delay = 60 * 3, delayRange = (int) (60 * 1.25);
	private BukkitTask task;
	private ArrayList<Player> players = new ArrayList<Player>();

	private boolean paused = false, generatingChunks = false;

	private final static String PLUGIN_NAME = "[Deathswap]: ";

	public void onEnable()
	{
		for (String command : getDescription().getCommands().keySet())
			getServer().getPluginCommand(command).setExecutor(this);

		getServer().getPluginManager().registerEvents(this, this);
	}

	private void start()
	{
		if (Bukkit.getOnlinePlayers().isEmpty())
			return;
		int chunkGenTime = 10;
		Bukkit.broadcastMessage(PLUGIN_NAME + "Starting deathswap! Allow " + chunkGenTime + " seconds for chunks to generate before starting");

		Random random = new Random();
		int x = 1000000 - random.nextInt(2000000);
		int z = 1000000 - random.nextInt(2000000);
		int minDistance = 5000;
		int maxRange = 100000;
		boolean respectTeams = false;
		String players = "@a"; // Here you specify a list of player names separated by spaces, or use
								// commandblock specifiers.

		Bukkit.getServer().getWorlds().forEach((world) -> world.setTime(0));

		ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
		Bukkit.getServer().dispatchCommand(console, String.format("spreadplayers %d %d %d %d %b %s", x, z, minDistance, maxRange, respectTeams, players));

		float moveSpeed = Bukkit.getOnlinePlayers().stream().findFirst().get().getWalkSpeed();
		DeathSwap.this.players.clear();
		for (Player player : Bukkit.getOnlinePlayers())
		{
			DeathSwap.this.players.add(player);
			player.setGameMode(GameMode.SURVIVAL);
			player.setWalkSpeed(0.0f);
			player.getInventory().clear();
			player.setExp(0);
			player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
			player.setSaturation(5);
			player.setFoodLevel(10000);
		}

		Collections.shuffle(DeathSwap.this.players);

		for (Player player : DeathSwap.this.players)
		{
			player.sendMessage(PLUGIN_NAME + "Your prey is " + getPreyAssignment(player));
		}
		generatingChunks = true;
		paused = false;

		// Delay starting the plugin to allow for chunks to generate
		(new BukkitRunnable()
		{

			@Override
			public void run()
			{
				Bukkit.broadcastMessage(PLUGIN_NAME + "Starting deathswap! Swaps happen every " + delay + " +- " + delayRange + " seconds");
				for (Player player : DeathSwap.this.players)
					player.setWalkSpeed(moveSpeed);

				DeathSwap.this.task = (new BukkitRunnable()
				{
					int timer = delay;

					public void run()
					{
						if (DeathSwap.this.players.isEmpty())
							stop();
						if (DeathSwap.this.paused)
							return;
						if (this.timer <= 3 && this.timer != 0)
							Bukkit.broadcastMessage(
									ChatColor.RED + "" + PLUGIN_NAME + ChatColor.BOLD + "Swapping in " + this.timer + ((this.timer == 1) ? " second!" : " seconds!"));
						if (this.timer <= 0)
						{
							swap();
							this.timer = delay + (int) ((1.0 - Math.random() * 2.0) * delayRange);
							Bukkit.broadcastMessage(
									ChatColor.GREEN + "" + PLUGIN_NAME + ChatColor.BOLD + "Swapping in " + this.timer + ((this.timer == 1) ? " second!" : " seconds!"));
						} else
						{
							this.timer--;
						}
					}
				}).runTaskTimer((Plugin) DeathSwap.this, 0L, 20L);
				generatingChunks = false;
			}
		}).runTaskLater(this, 20 * chunkGenTime);
	}

	// Players are always teleported to the next index in the array
	// Returns who the given player will be teleported
	private Player getPreyAssignment(Player player)
	{
		int index = players.indexOf(player);
		if (index == -1)
			throw new RuntimeException("Player " + player.getName() + " isnt playing in the game!");
		return players.get((index + 1) % players.size());
	}

	// Returns which player will be teleported to the given player on a swap
	private Player getPredatorAssignment(Player player)
	{
		int index = players.indexOf(player);
		if (index == -1)
			throw new RuntimeException("Player " + player.getName() + " isnt playing in the game!");
		// add players.size() to account for java's bad mod rules.
		// -1 % players.size() should be players.size() -1 but it isnt
		return players.get((index - 1 + players.size()) % players.size());
	}

	protected void swap()
	{
		ArrayList<Location> locations = new ArrayList<Location>();
		for (Player player : players)// Move all players except for the last one
		{
			locations.add(getPredatorAssignment(player).getLocation());
		}
		for (int i = 0; i < locations.size(); i++)
		{
			players.get(i).teleport(locations.get(i));
		}
	}

	private void stop()
	{
		if (this.task != null)
		{
			this.task.cancel();
			this.task = null;
		}
		generatingChunks = false;
	}

	public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
	{
		if (command.getName().equalsIgnoreCase("deathswap"))
		{
			if (args.length == 1)
			{
				if (args[0].equalsIgnoreCase("start"))
				{
					if (this.task != null)
					{
						sender.sendMessage(ChatColor.GREEN + PLUGIN_NAME + "Death swap is already started.");
						return false;
					}
					start();
					sender.sendMessage(ChatColor.GREEN + PLUGIN_NAME + "Death swap started.");
					return true;
				}
				if (args[0].equalsIgnoreCase("stop"))
				{
					stop();
					sender.sendMessage(ChatColor.RED + PLUGIN_NAME + "Death swap stopped.");
					return true;
				}
				return false;
			}
			if (args.length == 2)
			{
				if (args[0].equalsIgnoreCase("remove"))
				{
					Player player = Bukkit.getPlayer(args[1]);
					if (player == null)
					{
						sender.sendMessage(ChatColor.RED + PLUGIN_NAME + "Player " + args[1] + " not found.");
						return false;
					}
					removePlayer(player);
					return true;
				} else if (args[0].equalsIgnoreCase("swaptime"))
				{
					try
					{
						int newSwapTime = Integer.parseInt(args[1]);
						delay = newSwapTime;
						Bukkit.broadcastMessage(PLUGIN_NAME + sender.getName() + " set the swaptime to " + newSwapTime + " seconds");
					} catch (Exception e)
					{
						// Ignore
					}
				} else if (args[0].equalsIgnoreCase("swaprange"))
				{
					try
					{
						int newSwapRange = Integer.parseInt(args[1]);
						delayRange = newSwapRange;
						Bukkit.broadcastMessage(PLUGIN_NAME + sender.getName() + " set the swap time range to +- " + newSwapRange + " seconds");
					} catch (Exception e)
					{
						// Ignore
					}
				}
			} else if (args.length == 3)
			{
			}
			sender.sendMessage(ChatColor.RED + PLUGIN_NAME + "Invalid usage. Please use:");
			sender.sendMessage(ChatColor.RED + PLUGIN_NAME + "/deathswap remove <player>");
			sender.sendMessage(ChatColor.RED + PLUGIN_NAME + "/deathswap start");
			sender.sendMessage(ChatColor.RED + PLUGIN_NAME + "/deathswap swaptime <swaptime (seconds)>");
			sender.sendMessage(ChatColor.RED + PLUGIN_NAME + "/deathswap swaprange <swaprange (seconds)>");
			sender.sendMessage(ChatColor.RED + PLUGIN_NAME + "/deathswap stop");
			return false;
		}
		return false;
	}

	private void removePlayer(Player player)
	{
		player.sendMessage(ChatColor.YELLOW + PLUGIN_NAME + player.getName() + " Is out of the game");

		players.remove(player);
		if (players.size() <= 1)
		{
			String winnerName;
			if (players.size() == 1)
			{
				winnerName = players.get(0).getName();
			} else
			{
				winnerName = player.getName();
			}
			Bukkit.broadcastMessage(ChatColor.GREEN + PLUGIN_NAME + winnerName + " Won the game!");
			stop();
		}
	}

	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent event)
	{
		try
		{
			Player player = event.getEntity();
			Player killer = getPredatorAssignment(player);
			player.sendMessage(ChatColor.YELLOW + PLUGIN_NAME + player.getName() + " Was killed by " + killer.getName());
			removePlayer(player);
			if (!players.isEmpty())
			{
				killer.sendMessage(PLUGIN_NAME + "Your new prey is " + getPreyAssignment(killer));
			}
			player.setGameMode(GameMode.SPECTATOR);
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	@EventHandler
	public void onPlayerQuitEvent(PlayerQuitEvent event)
	{
		removePlayer(event.getPlayer());
	}

	@EventHandler(ignoreCancelled = false)
	public void onMove(PlayerMoveEvent e)
	{
		if (generatingChunks)
		{
			Location from = e.getFrom(), to = e.getTo();
			if (to.getY() > from.getY())
			{
				e.setCancelled(true);

			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockDamage(BlockDamageEvent e)
	{
		if (generatingChunks)
		{
			e.setCancelled(true);
		}
	}
}
