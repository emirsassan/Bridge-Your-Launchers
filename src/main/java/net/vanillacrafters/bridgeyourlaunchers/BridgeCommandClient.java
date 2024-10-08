package net.vanillacrafters.bridgeyourlaunchers;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

public class BridgeCommandClient implements ClientModInitializer {

    private static final Logger LOGGER = Logger.getLogger("BridgeCommandClient");
    private static final String configFolderName = "bridgeyourlaunchers";
    private static final String profilesFolderName = "profiles";
    private static final String readmeFileName = "readme.txt";
    private static final String configFileName = "command_configs.json";
    // Send Message to Client
    private static void sendChatMessage(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.of(message), false);
        }
    }

    @Override
    public void onInitializeClient() {
        // Create config folder and files
        createConfigFolderAndFiles();

        // Register packet to handle server's request
        ClientPlayNetworking.registerGlobalReceiver(new Identifier("bridgeyourlaunchers", "bridge_player"), (client, handler, buf, responseSender) -> {
            String profile = buf.readString();  // Profil adını client tarafında okuyoruz

            client.execute(() -> {
                try {
                    LOGGER.info("Bridge command received for profile: " + profile);

                    // Check for .url files in the specified profile folder
                    String minecraftDir = MinecraftClient.getInstance().runDirectory.getAbsolutePath();
                    File profileDir = new File(minecraftDir + File.separator + "config" + File.separator + configFolderName + File.separator + profilesFolderName + File.separator + profile);

                    Optional<File> urlFile = findUrlFile(profileDir);
                    PacketByteBuf responseBuf = PacketByteBufs.create();
                    responseBuf.writeBoolean(urlFile.isPresent());

                    // Send the result back to the server
                    ClientPlayNetworking.send(new Identifier("bridgeyourlaunchers", "file_check_response"), responseBuf);

                    if (urlFile.isPresent()) {
                        try {
                            // Açılacak URL dosyası varsa komutu çalıştır
                            Runtime.getRuntime().exec("cmd /c start \"\" \"" + urlFile.get().getAbsolutePath() + "\"");
                            sendChatMessage(client, "URL file opened successfully: " + urlFile.get().getAbsolutePath());

                            // Server'a paket göndererek oyuncu adına komut çalıştır
                            sendCommandToServer(client, "say yes");
                        } catch (IOException e) {
                            LOGGER.severe("Failed to open URL file: " + e.getMessage());
                            sendChatMessage(client, "Failed to open URL file: " + e.getMessage());
                        }
                        MinecraftClient.getInstance().scheduleStop();
                        LOGGER.info("Minecraft client scheduled to stop.");
                    } else {
                        sendChatMessage(client, "No URL file found in profile: " + profile);

                        // Server'a paket göndererek oyuncu adına komut çalıştır
                        sendCommandToServer(client, "say no");
                    }
                } catch (Exception e) {
                    LOGGER.severe("Error handling packet: " + e.getMessage());
                    sendChatMessage(client, "Error handling packet: " + e.getMessage());
                }
            });
        });
    }


    private void sendCommandToServer(MinecraftClient client, String command) {
        if (client.player != null) {
            // Send a command to the server (replace this with appropriate packet sending logic)
            client.player.networkHandler.sendChatCommand(command);
        }
    }

    private Optional<File> findUrlFile(File profileFolder) {
        File[] files = profileFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".url"));
        if (files != null && files.length > 0) {
            return Optional.of(files[0]);
        }
        return Optional.empty();
    }

    private void createConfigFolderAndFiles() {
        String minecraftDir = MinecraftClient.getInstance().runDirectory.getAbsolutePath();
        File configDir = new File(minecraftDir + File.separator + "config" + File.separator + configFolderName);
        File profilesDir = new File(configDir + File.separator + profilesFolderName);
        File readmeFile = new File(configDir, readmeFileName);
        File configFileJson = new File(configDir, configFileName);

        // Create the folders if they don't exist
        if (!configDir.exists()) {
            configDir.mkdirs();
            LOGGER.info("Created config folder: " + configDir.getAbsolutePath());
        }

        if (!profilesDir.exists()) {
            profilesDir.mkdirs();
            LOGGER.info("Created profiles folder: " + profilesDir.getAbsolutePath());
        }

        if (!configFileJson.exists()) {
            try (FileWriter writer = new FileWriter(configFileJson)) {
                writer.write("{\n" +
                        "  \"ifFileFound\": \"execute as <player> at @s run say File found. Example command executed.\",\n" +
                        "  \"ifFileNotFound\": \"execute as <player> at @s run say File not found. Example command executed.\"\n" +
                        "}");
                LOGGER.info("Created config file: " + configFileJson.getAbsolutePath());
            } catch (IOException e) {
                LOGGER.severe("Failed to create config file: " + e.getMessage());
            }
        }

        // Create the readme.txt file
        if (!readmeFile.exists()) {
            try (FileWriter writer = new FileWriter(readmeFile)) {
                writer.write("This is the readme file for Bridge Your Launchers mod.");
                LOGGER.info("Created readme file: " + readmeFile.getAbsolutePath());
            } catch (IOException e) {
                LOGGER.severe("Failed to create readme file: " + e.getMessage());
            }
        }
    }
}
