import os
import re

BASE_DIR = "/media/doohaer/ProjectDocs/Minecraft/IMYVM/src/WorldGeo-CommunityAddon"
SRC_DIRS = [
    os.path.join(BASE_DIR, "src/main/kotlin"),
    os.path.join(BASE_DIR, "src/main/java"),
]

replacements = [
    # Full qualified import replacements (more specific first)
    ("net.minecraft.entity.player.PlayerEntity", "net.minecraft.world.entity.player.Player"),
    ("net.minecraft.entity.player.PlayerInventory", "net.minecraft.world.entity.player.Inventory"),
    ("net.minecraft.server.network.ServerPlayerEntity", "net.minecraft.server.level.ServerPlayer"),
    ("net.minecraft.server.command.ServerCommandSource", "net.minecraft.commands.CommandSourceStack"),
    ("net.minecraft.server.command.CommandManager", "net.minecraft.commands.Commands"),
    ("net.minecraft.text.MutableText", "net.minecraft.network.chat.MutableComponent"),
    ("net.minecraft.text.Text", "net.minecraft.network.chat.Component"),
    ("net.minecraft.text.ClickEvent", "net.minecraft.network.chat.ClickEvent"),
    ("net.minecraft.text.HoverEvent", "net.minecraft.network.chat.HoverEvent"),
    ("net.minecraft.text.Style", "net.minecraft.network.chat.Style"),
    ("net.minecraft.text.TextColor", "net.minecraft.network.chat.TextColor"),
    ("net.minecraft.util.Formatting", "net.minecraft.ChatFormatting"),
    ("net.minecraft.screen.AnvilScreenHandler", "net.minecraft.world.inventory.AnvilMenu"),
    ("net.minecraft.screen.NamedScreenHandlerFactory", "net.minecraft.world.MenuProvider"),
    ("net.minecraft.screen.ScreenHandlerContext", "net.minecraft.world.inventory.ContainerLevelAccess"),
    ("net.minecraft.screen.ScreenHandlerType", "net.minecraft.world.inventory.MenuType"),
    ("net.minecraft.screen.ScreenHandler", "net.minecraft.world.inventory.AbstractContainerMenu"),
    ("net.minecraft.screen.slot.SlotActionType", "net.minecraft.world.inventory.ContainerInput"),
    ("net.minecraft.screen.slot.Slot", "net.minecraft.world.inventory.Slot"),
    ("net.minecraft.inventory.SimpleInventory", "net.minecraft.world.SimpleContainer"),
    ("net.minecraft.inventory.Inventory", "net.minecraft.world.Container"),
    ("net.minecraft.item.ItemStack", "net.minecraft.world.item.ItemStack"),
    ("net.minecraft.item.Items", "net.minecraft.world.item.Items"),
    ("net.minecraft.item.Item", "net.minecraft.world.item.Item"),
    ("net.minecraft.entity.effect.StatusEffectInstance", "net.minecraft.world.effect.MobEffectInstance"),
    ("net.minecraft.entity.effect.StatusEffects", "net.minecraft.world.effect.MobEffects"),
    ("net.minecraft.particle.ParticleTypes", "net.minecraft.core.particles.ParticleTypes"),
    ("net.minecraft.component.DataComponentTypes", "net.minecraft.core.component.DataComponents"),
    ("net.minecraft.component.type.LoreComponent", "net.minecraft.world.item.component.ItemLore"),
    ("net.minecraft.component.type.ProfileComponent", "net.minecraft.world.item.component.ResolvableProfile"),
    ("net.minecraft.network.message.SignedMessage", "net.minecraft.network.chat.PlayerChatMessage"),
    # Static method imports from CommandManager
    ("net.minecraft.server.command.CommandManager.literal", "net.minecraft.commands.Commands.literal"),
    ("net.minecraft.server.command.CommandManager.argument", "net.minecraft.commands.Commands.argument"),
    # Type name renames (in code, not just imports) - more specific first
    ("PlayerInventory", "Inventory"),
    ("PlayerEntity", "Player"),
    ("ServerPlayerEntity", "ServerPlayer"),
    ("ServerCommandSource", "CommandSourceStack"),
    ("MutableText", "MutableComponent"),
    ("ScreenHandlerType", "MenuType"),
    ("ScreenHandlerContext", "ContainerLevelAccess"),
    ("NamedScreenHandlerFactory", "MenuProvider"),
    ("AnvilScreenHandler", "AnvilMenu"),
    ("ScreenHandler", "AbstractContainerMenu"),
    ("SlotActionType", "ContainerInput"),
    ("SimpleInventory", "SimpleContainer"),
    ("StatusEffectInstance", "MobEffectInstance"),
    ("StatusEffects", "MobEffects"),
    ("DataComponentTypes", "DataComponents"),
    ("LoreComponent", "ItemLore"),
    ("ProfileComponent", "ResolvableProfile"),
    ("SignedMessage", "PlayerChatMessage"),
    # Text static calls (Text. -> Component.)
    ("Text.literal(", "Component.literal("),
    ("Text.translatable(", "Component.translatable("),
    ("Text.empty(", "Component.empty("),
    ("Text.of(", "Component.of("),
    ("Text.stringify(", "Component.stringify("),
    # Formatting references in code
    ("Formatting.", "ChatFormatting."),
    # Method renames
    ("getUuid()", "getUUID()"),
    ("sendMessage(", "sendSystemMessage("),
    ("sendFeedback(", "sendSuccess("),
    ("isExecutedByPlayer()", "isPlayer()"),
]

def process_file(path):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    
    original = content
    for old, new in replacements:
        content = content.replace(old, new)
    
    if content != original:
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"  Modified: {path}")
    else:
        print(f"  Unchanged: {path}")

total = 0
for src_dir in SRC_DIRS:
    if not os.path.exists(src_dir):
        continue
    for root, dirs, files in os.walk(src_dir):
        for fname in files:
            if fname.endswith(".kt") or fname.endswith(".java"):
                process_file(os.path.join(root, fname))
                total += 1

print(f"\nProcessed {total} files.")
