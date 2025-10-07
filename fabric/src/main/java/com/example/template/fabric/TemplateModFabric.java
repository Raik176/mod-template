package com.example.template.fabric;

import dev.kikugie.fletching_table.annotation.fabric.Entrypoint;
import net.fabricmc.api.ModInitializer;
import com.example.template.TemplateModCommon;

@Entrypoint
public class TemplateModFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		TemplateModCommon.init();
	}
}
