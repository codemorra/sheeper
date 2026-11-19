// Keep arena breathing and floor collision positional; unify water in the client renderer.
// Physics comparisons in Fluid/WaterFluid are deliberately left untouched.
function initializeCoreMod() {
  var O = Java.type("org.objectweb.asm.Opcodes");
  var List = Java.type("org.objectweb.asm.tree.InsnList");
  var Var = Java.type("org.objectweb.asm.tree.VarInsnNode");
  var Call = Java.type("org.objectweb.asm.tree.MethodInsnNode");
  var Insn = Java.type("org.objectweb.asm.tree.InsnNode");
  var Jump = Java.type("org.objectweb.asm.tree.JumpInsnNode");
  var Label = Java.type("org.objectweb.asm.tree.LabelNode");
  var Type = Java.type("org.objectweb.asm.tree.TypeInsnNode");
  var client = "de/codemorra/sheeper/client/WaterArenaVisuals";
  var fluid = "Lnet/minecraft/world/level/material/Fluid;";
  var state = "Lnet/minecraft/world/level/material/FluidState;";
  var getter = "Lnet/minecraft/world/level/BlockAndTintGetter;";
  var pos = "Lnet/minecraft/core/BlockPos;";

  function invoke(owner, name, desc) {
    return new Call(O.INVOKESTATIC, owner, name, desc, false);
  }
  function check(count, expected, target) {
    if (count !== expected) {
      throw new Error("Arena water hook mismatch in " + target + ": " + count + " targets");
    }
  }
  return {
    arena_water_floor_collision: {
      target: { type: "CLASS", name: "net.minecraft.world.entity.Entity" },
      transformer: function (node) {
        var count = 0;
        var vector = "Lnet/minecraft/world/phys/Vec3;";
        var entity = "Lnet/minecraft/world/entity/Entity;";
        for (var i = 0; i < node.methods.size(); i++) {
          var m = node.methods.get(i);
          if (String(m.desc) !== "(" + vector + ")" + vector ||
              (m.access & O.ACC_PRIVATE) === 0) continue;
          var instructions = m.instructions.toArray();
          for (var j = 0; j < instructions.length; j++) {
            var insn = instructions[j];
            if ((insn.getOpcode() === O.INVOKEVIRTUAL || insn.getOpcode() === O.INVOKEINTERFACE) &&
                String(insn.desc) === "(" + entity + "Lnet/minecraft/world/phys/AABB;)Ljava/util/List;") {
              var code = new List();
              code.add(new Var(O.ALOAD, 0));
              code.add(new Var(O.ALOAD, 1));
              code.add(invoke("de/codemorra/sheeper/world/ArenaWater", "floorCollisions",
                  "(Ljava/util/List;" + entity + vector + ")Ljava/util/List;"));
              m.instructions.insert(insn, code);
              count++;
            }
          }
        }
        check(count, 1, "Entity.collide entity collision shapes");
        return node;
      }
    },
    arena_water_breathing: {
      target: { type: "CLASS", name: "net.minecraftforge.common.extensions.IForgeLivingEntity" },
      transformer: function (node) {
        var count = 0;
        for (var i = 0; i < node.methods.size(); i++) {
          var m = node.methods.get(i);
          if (String(m.name) === "canDrownInFluidType" &&
              String(m.desc) === "(Lnet/minecraftforge/fluids/FluidType;)Z") {
            var code = new List();
            var vanilla = new Label();
            code.add(new Var(O.ALOAD, 0));
            code.add(new Type(O.CHECKCAST, "net/minecraft/world/entity/LivingEntity"));
            code.add(new Var(O.ALOAD, 1));
            code.add(invoke("de/codemorra/sheeper/world/ArenaWater", "allowsBreathing",
                "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraftforge/fluids/FluidType;)Z"));
            code.add(new Jump(O.IFEQ, vanilla));
            code.add(new Insn(O.ICONST_0));
            code.add(new Insn(O.IRETURN));
            code.add(vanilla);
            m.instructions.insert(code);
            count++;
          }
        }
        check(count, 1, "IForgeLivingEntity.canDrownInFluidType");
        return node;
      }
    },
    arena_water_camera: {
      target: { type: "CLASS", name: "net.minecraft.client.Camera" },
      transformer: function (node) {
        var count = 0;
        for (var i = 0; i < node.methods.size(); i++) {
          var m = node.methods.get(i);
          if (String(m.desc) === "()Lnet/minecraft/world/level/material/FogType;") {
            var code = new List();
            var vanilla = new Label();
            code.add(new Var(O.ALOAD, 0));
            code.add(invoke(client, "cameraFluid",
                "(Lnet/minecraft/client/Camera;)Lnet/minecraft/world/level/material/FogType;"));
            code.add(new Insn(O.DUP));
            code.add(new Jump(O.IFNULL, vanilla));
            code.add(new Insn(O.ARETURN));
            code.add(vanilla);
            code.add(new Insn(O.POP));
            m.instructions.insert(code);
            count++;
          }
        }
        check(count, 1, "Camera.getFluidInCamera");
        return node;
      }
    },
    arena_water_rendering: {
      target: { type: "CLASS", name: "net.minecraft.client.renderer.block.LiquidBlockRenderer" },
      transformer: function (node) {
        var comparisons = 0;
        var tints = 0;
        for (var i = 0; i < node.methods.size(); i++) {
          var m = node.methods.get(i);
          var instructions = m.instructions.toArray();
          for (var j = 0; j < instructions.length; j++) {
            var insn = instructions[j];
            if (insn.getOpcode() === O.INVOKEVIRTUAL &&
                String(insn.owner) === "net/minecraft/world/level/material/Fluid" &&
                String(insn.desc) === "(" + fluid + ")Z") {
              m.instructions.set(insn, invoke(client, "sameFluid", "(" + fluid + fluid + ")Z"));
              comparisons++;
            }
            if (String(m.desc) === "(" + getter + pos +
                "Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;" + state + ")V" &&
                insn.getOpcode() === O.INVOKEINTERFACE &&
                String(insn.owner) === "net/minecraftforge/client/extensions/common/IClientFluidTypeExtensions" &&
                String(insn.name) === "getTintColor" &&
                String(insn.desc) === "(" + state + getter + pos + ")I") {
              var tint = new List();
              tint.add(new Var(O.ALOAD, 1));
              tint.add(new Var(O.ALOAD, 2));
              tint.add(new Var(O.ALOAD, 5));
              tint.add(invoke(client, "tintColor", "(I" + getter + pos + state + ")I"));
              m.instructions.insert(insn, tint);
              tints++;
            }
          }
        }
        check(comparisons, 3, "LiquidBlockRenderer fluid comparisons");
        check(tints, 1, "LiquidBlockRenderer water tint");
        return node;
      }
    }
  };
}
