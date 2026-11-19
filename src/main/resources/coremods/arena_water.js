// Provide virtual arena swimming, breathing, particles and camera effects without changing fluid blocks.
// Physics comparisons in Fluid/WaterFluid are deliberately left untouched.
function initializeCoreMod() {
  var O = Java.type("org.objectweb.asm.Opcodes");
  var List = Java.type("org.objectweb.asm.tree.InsnList");
  var Var = Java.type("org.objectweb.asm.tree.VarInsnNode");
  var Call = Java.type("org.objectweb.asm.tree.MethodInsnNode");
  var Insn = Java.type("org.objectweb.asm.tree.InsnNode");
  var Field = Java.type("org.objectweb.asm.tree.FieldInsnNode");
  var Jump = Java.type("org.objectweb.asm.tree.JumpInsnNode");
  var Label = Java.type("org.objectweb.asm.tree.LabelNode");
  var Type = Java.type("org.objectweb.asm.tree.TypeInsnNode");
  var client = "de/codemorra/sheeper/client/WaterArenaVisuals";
  var state = "Lnet/minecraft/world/level/material/FluidState;";
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
    arena_virtual_water_entity: {
      target: { type: "CLASS", name: "net.minecraft.world.entity.Entity" },
      transformer: function (node) {
        var heights = 0;
        var eyes = 0;
        var entity = "Lnet/minecraft/world/entity/Entity;";
        var fluidType = "Lnet/minecraftforge/fluids/FluidType;";
        function waterType(code) {
          code.add(new Field(O.GETSTATIC, "net/minecraftforge/common/ForgeMod", "WATER_TYPE",
              "Lnet/minecraftforge/registries/RegistryObject;"));
          code.add(new Call(O.INVOKEVIRTUAL, "net/minecraftforge/registries/RegistryObject", "get",
              "()Ljava/lang/Object;", false));
          code.add(new Type(O.CHECKCAST, "net/minecraftforge/fluids/FluidType"));
        }
        for (var i = 0; i < node.methods.size(); i++) {
          var m = node.methods.get(i);
          if (String(m.name) === "updateFluidHeightAndDoFluidPushing" && String(m.desc) === "()V") {
            var instructions = m.instructions.toArray();
            var heightSlot = m.maxLocals;
            m.maxLocals += 2;
            for (var j = 0; j < instructions.length; j++) {
              if (instructions[j].getOpcode() !== O.RETURN) continue;
              var code = new List();
              var dry = new Label();
              code.add(new Var(O.ALOAD, 0));
              code.add(invoke("de/codemorra/sheeper/world/ArenaWater", "waterHeight", "(" + entity + ")D"));
              code.add(new Var(O.DSTORE, heightSlot));
              code.add(new Var(O.DLOAD, heightSlot));
              code.add(new Insn(O.DCONST_0));
              code.add(new Insn(O.DCMPL));
              code.add(new Jump(O.IFLE, dry));
              code.add(new Var(O.ALOAD, 0));
              waterType(code);
              code.add(new Var(O.DLOAD, heightSlot));
              code.add(new Call(O.INVOKEVIRTUAL, "net/minecraft/world/entity/Entity", "setFluidTypeHeight",
                  "(" + fluidType + "D)V", false));
              code.add(dry);
              m.instructions.insertBefore(instructions[j], code);
            }
            heights++;
          }
          if (String(m.name) === "getEyeInFluidType" && String(m.desc) === "()" + fluidType) {
            var code = new List();
            var physical = new Label();
            code.add(new Var(O.ALOAD, 0));
            code.add(invoke("de/codemorra/sheeper/world/ArenaWater", "isEyeSubmerged", "(" + entity + ")Z"));
            code.add(new Jump(O.IFEQ, physical));
            waterType(code);
            code.add(new Insn(O.ARETURN));
            code.add(physical);
            m.instructions.insert(code);
            eyes++;
          }
        }
        check(heights, 1, "Entity virtual water height");
        check(eyes, 1, "Entity virtual eye fluid");
        return node;
      }
    },
    arena_virtual_water_player_swimming: {
      target: { type: "CLASS", name: "net.minecraft.world.entity.player.Player" },
      transformer: function (node) {
        var count = 0;
        for (var i = 0; i < node.methods.size(); i++) {
          var m = node.methods.get(i);
          if (String(m.desc) !== "(Lnet/minecraft/world/phys/Vec3;)V") continue;
          var instructions = m.instructions.toArray();
          for (var j = 0; j < instructions.length; j++) {
            var insn = instructions[j];
            if (insn.getOpcode() !== O.INVOKEVIRTUAL ||
                String(insn.owner) !== "net/minecraft/world/level/material/FluidState" ||
                String(insn.desc) !== "()Z") continue;
            var code = new List();
            code.add(new Var(O.ALOAD, 0));
            m.instructions.insertBefore(insn, code);
            m.instructions.set(insn, invoke("de/codemorra/sheeper/world/ArenaWater", "isEmptyForSwimming",
                "(" + state + "Lnet/minecraft/world/entity/Entity;)Z"));
            count++;
          }
        }
        check(count, 1, "Player virtual look-directed swimming");
        return node;
      }
    },
    arena_water_push_movement: {
      target: { type: "CLASS", name: "net.minecraft.world.entity.Entity" },
      transformer: function (node) {
        var count = 0;
        for (var i = 0; i < node.methods.size(); i++) {
          var method = node.methods.get(i);
          if (String(method.desc) !== "(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V") continue;
          var code = new List();
          code.add(new Var(O.ALOAD, 0));
          code.add(new Var(O.ALOAD, 2));
          code.add(invoke("de/codemorra/sheeper/world/WaterArenaPushProtection", "limitMovement",
              "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"));
          code.add(new Var(O.ASTORE, 2));
          method.instructions.insert(code);
          count++;
        }
        check(count, 1, "Entity.move protected boss impulses");
        return node;
      }
    },
    arena_water_boundary_collision: {
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
              code.add(invoke("de/codemorra/sheeper/world/ArenaWater", "boundaryCollisions",
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
    arena_water_bubble_particles: {
      target: { type: "CLASS", name: "net.minecraft.client.particle.BubbleParticle" },
      transformer: function (node) {
        var count = 0;
        for (var i = 0; i < node.methods.size(); i++) {
          var m = node.methods.get(i);
          var instructions = m.instructions.toArray();
          for (var j = 0; j < instructions.length; j++) {
            var insn = instructions[j];
            if (insn.getOpcode() === O.INVOKEVIRTUAL &&
                String(insn.owner) === "net/minecraft/client/multiplayer/ClientLevel" &&
                String(insn.desc) === "(" + pos + ")" + state) {
              var code = new List();
              code.add(new Var(O.ALOAD, 0));
              code.add(invoke(client, "bubbleFluid",
                  "(" + state + "Lnet/minecraft/client/particle/Particle;)" + state));
              m.instructions.insert(insn, code);
              count++;
            }
          }
        }
        check(count, 1, "BubbleParticle virtual water lifetime");
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
    }
  };
}
