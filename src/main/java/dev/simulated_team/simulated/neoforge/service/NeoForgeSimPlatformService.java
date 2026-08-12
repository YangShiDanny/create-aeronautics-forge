package dev.simulated_team.simulated.neoforge.service;

import dev.simulated_team.simulated.service.SimPlatformService;
import net.minecraftforge.fml.ModList;

public class NeoForgeSimPlatformService implements SimPlatformService {

	@Override
	public boolean isLoaded(final String modId) {
		return ModList.get().isLoaded(modId);
	}
}
