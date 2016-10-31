package com.arckenver.nations.task;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.carrier.Chest;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.service.economy.account.Account;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.service.economy.transaction.TransactionResult;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.channel.MessageChannel;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.World;

import com.arckenver.nations.ConfigHandler;
import com.arckenver.nations.DataHandler;
import com.arckenver.nations.LanguageHandler;
import com.arckenver.nations.NationsPlugin;
import com.arckenver.nations.object.Nation;
import com.flowpowered.math.vector.Vector3i;

import ninja.leaping.configurate.commented.CommentedConfigurationNode;

public class TaxesCollectRunnable implements Runnable
{
	public void run()
	{
		if (ConfigHandler.getNode("prices").getNode("upkeepPerCitizen").getDouble() == 0 && DataHandler.getNations().values().stream().allMatch(n -> n.getTaxes() == 0))
		{
			return;
		}
		MessageChannel.TO_ALL.send(Text.of(TextColors.AQUA, LanguageHandler.CL));
		ArrayList<UUID> nationsToRemove = new ArrayList<UUID>();
		for (Nation nation : DataHandler.getNations().values())
		{
			if (NationsPlugin.getEcoService() == null)
			{
				NationsPlugin.getLogger().error(LanguageHandler.DC);
				continue;
			}
			Optional<Account> optAccount = NationsPlugin.getEcoService().getOrCreateAccount("nation-" + nation.getUUID().toString());
			if (!optAccount.isPresent())
			{
				NationsPlugin.getLogger().error("Nation " + nation.getName() + " doesn't have an account on the economy plugin of this server");
				continue;
			}
			// nation taxes
			BigDecimal taxes = BigDecimal.valueOf(nation.getTaxes());
			for (UUID uuid : nation.getCitizens())
			{
				if (!nation.isStaff(uuid))
				{
					Optional<UniqueAccount> optCitizenAccount = NationsPlugin.getEcoService().getOrCreateAccount(uuid);
					TransactionResult result = optCitizenAccount.get().withdraw(NationsPlugin.getEcoService().getDefaultCurrency(), taxes, NationsPlugin.getCause());
					if (result.getResult() == ResultType.ACCOUNT_NO_FUNDS)
					{
						nation.removeCitizen(uuid);
						Sponge.getServer().getPlayer(uuid).ifPresent(p ->
									p.sendMessage(Text.of(TextColors.RED, LanguageHandler.HQ)));
					}
					else if (result.getResult() != ResultType.SUCCESS)
					{
						NationsPlugin.getLogger().error("Error while taking taxes from player " + uuid.toString() + " for nation " + nation.getName());
					}
					else
					{
						 TransactionResult res = optAccount.get().deposit(NationsPlugin.getEcoService().getDefaultCurrency(), taxes, NationsPlugin.getCause());
						 if (res.getResult() != ResultType.SUCCESS)
						 {
							 NationsPlugin.getLogger().error("Error while depositing taxes withdrawn from player " + uuid.toString() + " in nation " + nation.getName());
						 }
					}
				}
			}
			// nation upkeep
			if (ConfigHandler.getNode("others", "enableItemUpkeep").getBoolean())
			{
				Hashtable<ItemType, Integer> upkeep = new Hashtable<ItemType, Integer>();
				for (Entry<Object, ? extends CommentedConfigurationNode> e : ConfigHandler.getNode("itemUpkeepPerCitizen").getChildrenMap().entrySet())
				{
					Optional<ItemType> optType = Sponge.getRegistry().getType(ItemType.class, e.getKey().toString());
					if (optType.isPresent())
					{
						upkeep.put(optType.get(), nation.getNumCitizens() * e.getValue().getInt());
					}
					else
					{
						NationsPlugin.getLogger().error("Error while collecting item upkeeps: \"" + e.getKey().toString() + "\" is not an item type");
					}
				}
				Hashtable<UUID, ArrayList<Vector3i>> chestPositions = DataHandler.getChestPositions(nation.getUUID());
				if (chestPositions != null)
				{
					for (Entry<UUID, ArrayList<Vector3i>> e : chestPositions.entrySet())
					{
						World world = Sponge.getServer().getWorld(e.getKey()).get();
						for (Vector3i vect : e.getValue())
						{
							if (world.getBlockType(vect).equals(BlockTypes.CHEST) ||
									world.getBlockType(vect).equals(BlockTypes.TRAPPED_CHEST))
							{
								Chest chest = (Chest) world.getLocation(vect).getTileEntity().get();
								for (ItemType itemType : upkeep.keySet())
								{
									Optional<ItemStack> optItemStack = chest.getInventory().query(itemType).poll(upkeep.get(itemType));
									if (optItemStack.isPresent())
									{
										upkeep.put(itemType, upkeep.get(itemType) - optItemStack.get().getQuantity());
									}
								}
							}
						}
					}
					if (!upkeep.values().stream().allMatch(i -> i <= 0))
					{
						nationsToRemove.add(nation.getUUID());
					}
					
				}
				else
				{
					NationsPlugin.getLogger().error("Error while collecting item upkeeps: could not find chest positions for nation " + nation.getName());
				}
			}
			else
			{
				BigDecimal upkeep = BigDecimal.valueOf(nation.getUpkeep());
				TransactionResult result = optAccount.get().withdraw(NationsPlugin.getEcoService().getDefaultCurrency(), upkeep, NationsPlugin.getCause());
				if (result.getResult() == ResultType.ACCOUNT_NO_FUNDS)
				{
					nationsToRemove.add(nation.getUUID());
				}
				else if (result.getResult() != ResultType.SUCCESS)
				{
					NationsPlugin.getLogger().error("Error while taking upkeep from nation " + nation.getName());
				}
			}
		}
		for (UUID uuid : nationsToRemove)
		{
			String name = DataHandler.getNation(uuid).getName();
			DataHandler.removeNation(uuid);
			MessageChannel.TO_ALL.send(Text.of(TextColors.RED, LanguageHandler.CM.replaceAll(Pattern.quote("\\{NATION\\}"), name)));
		}
	}
}
