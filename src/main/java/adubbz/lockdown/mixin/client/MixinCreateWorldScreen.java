/*******************************************************************************
 * Copyright 2022, the Glitchfiend Team.
 * All rights reserved.
 ******************************************************************************/
package adubbz.lockdown.mixin.client;

import adubbz.lockdown.Config;
import adubbz.lockdown.Lockdown;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.commons.io.FileUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.IOException;

@Mixin(CreateWorldScreen.class)
public abstract class MixinCreateWorldScreen extends Screen {

    @Shadow
    public WorldCreationUiState uiState;

    protected MixinCreateWorldScreen(Component component) {
        super(component);
    }

    @Inject(method = "onCreate", at = @At(value = "HEAD"), cancellable = true)
    private void onCreate(CallbackInfo ci) {

        String mapName = this.uiState.getName().trim();
        File gameDirectory = this.minecraft.gameDirectory.getAbsoluteFile();
        File templateDirectory = new File(gameDirectory + File.separator + Config.templateDirectory.get());

        // Queue the load screen whilst we copy the template

        this.minecraft.forceSetScreen(new GenericDirtMessageScreen(Component.translatable("selectWorld.data_read")));

        // Copy the world
        try {
            String fileName = lockdown$checkSameMapName(gameDirectory, mapName, 0);
            FileUtils.copyDirectory(templateDirectory, new File(gameDirectory + File.separator + "saves" + File.separator + fileName));
        } catch (IOException e) {
            Lockdown.LOGGER.error("The template world does not exist at " + templateDirectory, e);
            return;
        }

        try {
            LevelStorageSource.LevelStorageAccess storageAccess = this.minecraft.getLevelSource().createAccess(mapName);
            // Rename the level for our new name
            storageAccess.renameLevel(mapName);
            // Close the storage access
            storageAccess.close();
        } catch (IOException e) {
            SystemToast.onWorldAccessFailure(this.minecraft, mapName);
            Lockdown.LOGGER.error("Failed to rename level {}", mapName, e);
        }

        // Load the level
        this.minecraft.createWorldOpenFlows().loadLevel(this, mapName);

        // Cancel to prevent normal world creation
        ci.cancel();
    }

    @Unique
    private String lockdown$checkSameMapName(File gameDirectory, String uiName, Integer startIndex) {
        String expectName = String.format("%s-%d", uiName, startIndex);
        if (startIndex == 0) {
            expectName = uiName;
        }
        File saveFile = new File(gameDirectory + File.separator + "saves" + File.separator + expectName);
        if (saveFile.exists()) {
            return lockdown$checkSameMapName(gameDirectory, uiName, startIndex + 1);
        } else {
            return expectName;
        }
    }
}
