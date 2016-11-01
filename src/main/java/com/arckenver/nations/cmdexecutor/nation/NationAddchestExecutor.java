package com.arckenver.nations.cmdexecutor.nation;

import java.util.Optional;

import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.blockray.BlockRay;
import org.spongepowered.api.util.blockray.BlockRayHit;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import com.arckenver.nations.DataHandler;
import com.arckenver.nations.LanguageHandler;
import com.arckenver.nations.object.Nation;

public class NationAddchestExecutor implements CommandExecutor
{
	public CommandResult execute(CommandSource src, CommandContext ctx) throws CommandException
	{
		if (src instanceof Player)
		{
			Player player = (Player) src;
			Nation nation = DataHandler.getNationOfPlayer(player.getUniqueId());
			if (nation == null)
			{
				src.sendMessage(Text.of(TextColors.RED, LanguageHandler.CI));
				return CommandResult.success();
			}
			if (!nation.isStaff(player.getUniqueId()))
			{
				src.sendMessage(Text.of(TextColors.RED, LanguageHandler.CJ));
				return CommandResult.success();
			}
			Optional<BlockRayHit<World>> optHit = BlockRay.from(player).skipFilter(BlockRay.continueAfterFilter(BlockRay.onlyAirFilter(), 1)).build().end();
			if (!optHit.isPresent())
			{
				src.sendMessage(Text.of(TextColors.RED, "No chest in sight (or too far)"));
				return CommandResult.success();
			}
			Location<World> loc = optHit.get().getLocation();
			if (!loc.getBlockType().equals(BlockTypes.CHEST) &&
					!loc.getBlockType().equals(BlockTypes.TRAPPED_CHEST))
			{
				src.sendMessage(Text.of(TextColors.RED, "No chest in sight (or too far)"));
				return CommandResult.success();
			}
			DataHandler.addChestPosition(nation.getUUID(), loc);
			src.sendMessage(Text.of(TextColors.GREEN, LanguageHandler.HL));
		}
		else
		{
			src.sendMessage(Text.of(TextColors.RED, LanguageHandler.CA));
		}
		return CommandResult.success();
	}
}
