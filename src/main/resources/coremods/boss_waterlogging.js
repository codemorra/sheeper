// Extend existing waterloggable blocks without changing vanilla WATERLOGGED semantics.
// Targets use stable class names and unique JVM descriptors rather than mapped method names.
// Keep calls compatible with Nashorn: trailing argument commas are not supported.
function initializeCoreMod() {
  var Opcodes = Java.type("org.objectweb.asm.Opcodes");
  var InsnList = Java.type("org.objectweb.asm.tree.InsnList");
  var InsnNode = Java.type("org.objectweb.asm.tree.InsnNode");
  var VarInsnNode = Java.type("org.objectweb.asm.tree.VarInsnNode");
  var MethodInsnNode = Java.type("org.objectweb.asm.tree.MethodInsnNode");
  var TypeInsnNode = Java.type("org.objectweb.asm.tree.TypeInsnNode");
  var LabelNode = Java.type("org.objectweb.asm.tree.LabelNode");
  var JumpInsnNode = Java.type("org.objectweb.asm.tree.JumpInsnNode");
  var hook = "de/codemorra/sheeper/fluid/BossWaterlogging";
  var state = "Lnet/minecraft/world/level/block/state/BlockState;";
  var fluid = "Lnet/minecraft/world/level/material/Fluid;";
  var fluidState = "Lnet/minecraft/world/level/material/FluidState;";
  var pos = "Lnet/minecraft/core/BlockPos;";
  var accessor = "Lnet/minecraft/world/level/LevelAccessor;";
  var getter = "Lnet/minecraft/world/level/BlockGetter;";
  var builder =
    "Lnet/minecraft/world/level/block/state/StateDefinition$Builder;";

  function invoke(name, desc) {
    return new MethodInsnNode(Opcodes.INVOKESTATIC, hook, name, desc, false);
  }

  function check(count, expected, target) {
    if (count !== expected) {
      throw new Error(
        "Boss water hook mismatch in " + target + ": " + count + " targets"
      );
    }
  }

  function decisionPrefix(method, name, desc, slots) {
    var code = new InsnList();
    for (var i = 0; i < slots.length; i++) {
      code.add(new VarInsnNode(Opcodes.ALOAD, slots[i]));
    }
    code.add(invoke(name, desc));
    code.add(new InsnNode(Opcodes.DUP));
    code.add(new InsnNode(Opcodes.ICONST_M1));
    var fallback = new LabelNode();
    code.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, fallback));
    code.add(new InsnNode(Opcodes.IRETURN));
    code.add(fallback);
    code.add(new InsnNode(Opcodes.POP));
    method.instructions.insert(code);
  }

  return {
    boss_water_properties: {
      target: { type: "CLASS", name: "net.minecraft.world.level.block.Block" },
      transformer: function (node) {
        var properties = 0;
        var defaults = 0;
        for (var m = 0; m < node.methods.size(); m++) {
          var method = node.methods.get(m);
          if (
            String(method.name) === "<init>" &&
            String(method.desc) ===
              "(Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V"
          ) {
            for (
              var insn = method.instructions.getFirst();
              insn !== null;
              insn = insn.getNext()
            ) {
              if (
                insn.getOpcode() === Opcodes.INVOKEVIRTUAL &&
                String(insn.desc) === "(" + builder + ")V"
              ) {
                var code = new InsnList();
                code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                code.add(new VarInsnNode(Opcodes.ALOAD, 2));
                code.add(
                  invoke(
                    "addProperty",
                    "(Lnet/minecraft/world/level/block/Block;" + builder + ")V"
                  )
                );
                method.instructions.insert(insn, code);
                properties++;
              }
            }
          }
          if (
            String(method.desc) === "(" + state + ")V" &&
            (method.access & Opcodes.ACC_FINAL) !== 0
          ) {
            var dry = new InsnList();
            dry.add(new VarInsnNode(Opcodes.ALOAD, 1));
            dry.add(invoke("dryDefault", "(" + state + ")" + state));
            dry.add(new VarInsnNode(Opcodes.ASTORE, 1));
            method.instructions.insert(dry);
            defaults++;
          }
        }
        check(properties, 1, "Block constructor");
        check(defaults, 1, "Block default state");
        return node;
      }
    },
    boss_water_state: {
      target: {
        type: "CLASS",
        name: "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase"
      },
      transformer: function (node) {
        var count = 0;
        for (var i = 0; i < node.methods.size(); i++) {
          var method = node.methods.get(i);
          // Shape maps in vanilla are keyed by the original dry block state.
          if (
            String(method.desc).endsWith(
              ")Lnet/minecraft/world/phys/shapes/VoxelShape;"
            )
          ) {
            for (
              var shapeInsn = method.instructions.getFirst();
              shapeInsn !== null;
              shapeInsn = shapeInsn.getNext()
            ) {
              if (
                shapeInsn.getOpcode() === Opcodes.INVOKEVIRTUAL &&
                String(shapeInsn.desc) === "()" + state
              ) {
                method.instructions.insert(
                  shapeInsn,
                  invoke("dryDefault", "(" + state + ")" + state)
                );
              }
            }
          }
          if (String(method.desc) === "()" + fluidState) {
            var code = new InsnList();
            code.add(new VarInsnNode(Opcodes.ALOAD, 0));
            code.add(
              new TypeInsnNode(
                Opcodes.CHECKCAST,
                "net/minecraft/world/level/block/state/BlockState"
              )
            );
            code.add(invoke("fluidOverride", "(" + state + ")" + fluidState));
            code.add(new InsnNode(Opcodes.DUP));
            var vanilla = new LabelNode();
            code.add(new JumpInsnNode(Opcodes.IFNULL, vanilla));
            code.add(new InsnNode(Opcodes.ARETURN));
            code.add(vanilla);
            code.add(new InsnNode(Opcodes.POP));
            method.instructions.insert(code);
            count++;
          }
        }
        check(count, 1, "BlockState fluid lookup");
        return node;
      }
    },
    boss_water_shape_cache: {
      target: {
        type: "CLASS",
        name: "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase$Cache"
      },
      transformer: function (node) {
        var count = 0;
        for (var i = 0; i < node.methods.size(); i++) {
          var method = node.methods.get(i);
          if (
            String(method.name) === "<init>" &&
            String(method.desc) === "(" + state + ")V"
          ) {
            var code = new InsnList();
            code.add(new VarInsnNode(Opcodes.ALOAD, 1));
            code.add(invoke("dryDefault", "(" + state + ")" + state));
            code.add(new VarInsnNode(Opcodes.ASTORE, 1));
            method.instructions.insert(code);
            count++;
          }
        }
        check(count, 1, "BlockState shape cache");
        return node;
      }
    },
    boss_water_container: {
      target: {
        type: "CLASS",
        name: "net.minecraft.world.level.block.SimpleWaterloggedBlock"
      },
      transformer: function (node) {
        var count = 0;
        for (var i = 0; i < node.methods.size(); i++) {
          var method = node.methods.get(i);
          if (
            String(method.desc) ===
            "(" + getter + pos + state + fluid + ")Z"
          ) {
            decisionPrefix(
              method,
              "canPlace",
              "(" + state + fluid + ")I",
              [3, 4]
            );
            count++;
          }
          if (
            String(method.desc) ===
            "(" + accessor + pos + state + fluidState + ")Z"
          ) {
            decisionPrefix(
              method,
              "place",
              "(" + accessor + pos + state + fluidState + ")I",
              [1, 2, 3, 4]
            );
            count++;
          }
        }
        check(count, 2, "SimpleWaterloggedBlock");
        return node;
      }
    }
  };
}
