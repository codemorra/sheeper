// Add positional rain without changing the dimension's global weather state.
function initializeCoreMod() {
  var O = Java.type("org.objectweb.asm.Opcodes");
  var List = Java.type("org.objectweb.asm.tree.InsnList");
  var Var = Java.type("org.objectweb.asm.tree.VarInsnNode");
  var Call = Java.type("org.objectweb.asm.tree.MethodInsnNode");
  var Insn = Java.type("org.objectweb.asm.tree.InsnNode");
  var Jump = Java.type("org.objectweb.asm.tree.JumpInsnNode");
  var Label = Java.type("org.objectweb.asm.tree.LabelNode");
  var API = Java.type("net.minecraftforge.coremod.api.ASMAPI");
  var hook = "de/codemorra/sheeper/world/ArenaWeather";
  var client = "de/codemorra/sheeper/client/LightningWeatherVisuals";
  var level = "Lnet/minecraft/world/level/Level;";
  var pos = "Lnet/minecraft/core/BlockPos;";

  function call(owner, name, desc) {
    return new Call(O.INVOKESTATIC, owner, name, desc, false);
  }
  function check(count, target) {
    if (count === 0) throw new Error("Arena weather hook not found: " + target);
  }
  return {
    arena_rain_at: {
      target: { type: "CLASS", name: "net.minecraft.world.level.Level" },
      transformer: function (node) {
        var count = 0;
        for (var i = 0; i < node.methods.size(); i++) {
          var m = node.methods.get(i);
          if (
            String(m.name) === API.mapMethod("m_46758_") &&
            String(m.desc) === "(" + pos + ")Z"
          ) {
            var code = new List();
            var vanilla = new Label();
            code.add(new Var(O.ALOAD, 0));
            code.add(new Var(O.ALOAD, 1));
            code.add(call(hook, "isRainingAt", "(" + level + pos + ")Z"));
            code.add(new Jump(O.IFEQ, vanilla));
            code.add(new Insn(O.ICONST_1));
            code.add(new Insn(O.IRETURN));
            code.add(vanilla);
            m.instructions.insert(code);
            count++;
          }
        }
        check(count, "Level.isRainingAt");
        return node;
      }
    },
    arena_rain_fire: {
      target: {
        type: "CLASS",
        name: "net.minecraft.world.level.block.FireBlock"
      },
      transformer: function (node) {
        var count = 0;
        for (var i = 0; i < node.methods.size(); i++) {
          var m = node.methods.get(i);
          var instructions = m.instructions.toArray();
          for (var j = 0; j < instructions.length; j++) {
            var insn = instructions[j];
            if (
              insn.getOpcode() === O.INVOKEVIRTUAL &&
              String(insn.name) === API.mapMethod("m_46471_") &&
              String(insn.desc) === "()Z"
            ) {
              m.instructions.set(
                insn,
                call(hook, "hasRain", "(" + level + ")Z")
              );
              count++;
            }
          }
        }
        check(count, "FireBlock rain guard");
        return node;
      }
    },
    arena_rain_precipitation: {
      target: { type: "CLASS", name: "net.minecraft.server.level.ServerLevel" },
      transformer: function (node) {
        var count = 0;
        for (var i = 0; i < node.methods.size(); i++) {
          var m = node.methods.get(i);
          if (String(m.name) === API.mapMethod("m_8714_")) {
            var code = new List();
            code.add(new Var(O.ALOAD, 0));
            code.add(new Var(O.ALOAD, 1));
            code.add(
              call(
                hook,
                "tickPrecipitation",
                "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;)V"
              )
            );
            m.instructions.insert(code);
            count++;
          }
        }
        check(count, "ServerLevel.tickChunk");
        return node;
      }
    },
    arena_storm_sky: {
      target: {
        type: "CLASS",
        name: "net.minecraft.client.multiplayer.ClientLevel"
      },
      transformer: function (node) {
        var count = 0;
        for (var i = 0; i < node.methods.size(); i++) {
          var m = node.methods.get(i);
          for (
            var insn = m.instructions.getFirst();
            insn !== null;
            insn = insn.getNext()
          ) {
            if (
              insn.getOpcode() === O.INVOKEVIRTUAL &&
              String(insn.desc) === "(F)F" &&
              (String(insn.name) === API.mapMethod("m_46722_") ||
                String(insn.name) === API.mapMethod("m_46661_"))
            ) {
              m.instructions.insert(insn, call(client, "skyWeather", "(F)F"));
              count++;
            }
          }
        }
        check(count, "ClientLevel sky and clouds");
        return node;
      }
    },
    arena_storm_precipitation_visuals: {
      target: {
        type: "CLASS",
        name: "net.minecraft.client.renderer.LevelRenderer"
      },
      transformer: function (node) {
        var count = 0;
        var biome = "Lnet/minecraft/world/level/biome/Biome;";
        for (var i = 0; i < node.methods.size(); i++) {
          var m = node.methods.get(i);
          if (
            String(m.name) !== API.mapMethod("m_109703_") &&
            String(m.name) !== API.mapMethod("m_109693_")
          )
            continue;
          var instructions = m.instructions.toArray();
          for (var j = 0; j < instructions.length; j++) {
            var insn = instructions[j];
            if (insn.getOpcode() !== O.INVOKEVIRTUAL) continue;
            if (
              String(insn.desc) ===
              "(" + pos + ")Lnet/minecraft/core/Holder;"
            ) {
              m.instructions.insertBefore(
                insn,
                call(client, "rainColumn", "(" + pos + ")" + pos)
              );
            } else if (String(insn.name) === API.mapMethod("m_47530_")) {
              m.instructions.set(
                insn,
                call(
                  client,
                  "precipitation",
                  "(" +
                    biome +
                    ")Lnet/minecraft/world/level/biome/Biome$Precipitation;"
                )
              );
              count++;
            } else if (String(insn.name) === API.mapMethod("m_198906_")) {
              m.instructions.set(
                insn,
                call(client, "warmEnough", "(" + biome + pos + ")Z")
              );
            }
          }
        }
        check(count, "LevelRenderer precipitation columns");
        return node;
      }
    }
  };
}
