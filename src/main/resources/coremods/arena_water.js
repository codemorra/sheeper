// Provide virtual arena swimming, breathing, particles and camera effects without changing fluid blocks.
// Physics comparisons in Fluid/WaterFluid are deliberately left untouched.
function initializeCoreMod() {
  var API = Java.type("net.minecraftforge.coremod.api.ASMAPI");
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
    arena_virtual_water_drowned_paths: {
      target: { type: "CLASS", name: "net.minecraft.world.level.pathfinder.SwimNodeEvaluator" },
      transformer: function (node) {
        var classify = 0;
        var cost = 0;
        var hooks = "de/codemorra/sheeper/world/DrownedArenaPaths";
        var blocks = "Lnet/minecraft/world/level/BlockGetter;";
        var mob = "Lnet/minecraft/world/entity/Mob;";
        var pathType = "Lnet/minecraft/world/level/pathfinder/BlockPathTypes;";
        for (var i = 0; i < node.methods.size(); i++) {
          var m = node.methods.get(i);
          if (String(m.name) === API.mapMethod("m_7209_") &&
              String(m.desc) === "(" + blocks + "III" + mob + "IIIZZ)" + pathType) {
            var code = new List();
            var vanilla = new Label();
            code.add(new Var(O.ALOAD, 1));
            for (var slot = 2; slot <= 8; slot++) {
              code.add(new Var(slot === 5 ? O.ALOAD : O.ILOAD, slot));
            }
            code.add(invoke(hooks, "classify", "(" + blocks + "III" + mob + "III)" + pathType));
            code.add(new Insn(O.DUP));
            code.add(new Jump(O.IFNULL, vanilla));
            code.add(new Insn(O.ARETURN));
            code.add(vanilla);
            code.add(new Insn(O.POP));
            m.instructions.insert(code);
            classify++;
          }
          if (String(m.name) !== API.mapMethod("m_5676_") ||
              String(m.desc) !== "(III)Lnet/minecraft/world/level/pathfinder/Node;") continue;
          var typeSlot = -1;
          var instructions = m.instructions.toArray();
          for (var j = 0; j < instructions.length; j++) {
            var insn = instructions[j];
            if (insn.getOpcode() !== O.INVOKEVIRTUAL) continue;
            if (String(insn.name) === API.mapMethod("m_192967_") && String(insn.desc) === "(III)" + pathType) {
              var store = insn.getNext();
              if (store !== null && store.getOpcode() === O.ASTORE) typeSlot = store.var;
            } else if (typeSlot >= 0 && String(insn.owner) === "net/minecraft/world/level/material/FluidState" &&
                String(insn.name) === API.mapMethod("m_76178_") && String(insn.desc) === "()Z") {
              var args = new List();
              args.add(new Var(O.ALOAD, 0));
              args.add(new Field(O.GETFIELD, String(node.name), API.mapField("f_77313_"), mob));
              args.add(new Var(O.ALOAD, typeSlot));
              m.instructions.insertBefore(insn, args);
              m.instructions.set(insn, invoke(hooks, "isDryNode", "(" + state + mob + pathType + ")Z"));
              cost++;
            }
          }
        }
        check(classify, 1, "SwimNodeEvaluator water classification");
        check(cost, 1, "SwimNodeEvaluator dry node cost");
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
          code.add(new Var(O.ALOAD, 1));
          code.add(new Var(O.ALOAD, 2));
          code.add(invoke("de/codemorra/sheeper/world/LightningArenaWind", "applyWind",
              "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"));
          code.add(new Var(O.ASTORE, 2));
          code.add(new Var(O.ALOAD, 0));
          code.add(new Var(O.ALOAD, 2));
          code.add(invoke("de/codemorra/sheeper/world/WaterArenaPushProtection", "limitMovement",
              "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"));
          code.add(new Var(O.ASTORE, 2));
          code.add(new Var(O.ALOAD, 0));
          code.add(new Var(O.ALOAD, 1));
          code.add(new Var(O.ALOAD, 2));
          code.add(invoke("de/codemorra/sheeper/world/WaterArenaBoundaryDrag", "limitMovement",
              "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"));
          code.add(new Var(O.ASTORE, 2));
          code.add(new Var(O.ALOAD, 0));
          code.add(new Var(O.ALOAD, 1));
          code.add(new Var(O.ALOAD, 2));
          code.add(invoke("de/codemorra/sheeper/world/LightningArenaBoundaryDrag", "limitMovement",
              "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"));
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
    arena_water_fluid_behavior: {
      target: { type: "CLASS", name: "net.minecraftforge.common.extensions.IForgeLivingEntity" },
      transformer: function (node) {
        var breathing = 0;
        var lavaLift = 0;
        var living = "Lnet/minecraft/world/entity/LivingEntity;";
        var fluidType = "Lnet/minecraftforge/fluids/FluidType;";
        for (var i = 0; i < node.methods.size(); i++) {
          var m = node.methods.get(i);
          if (String(m.name) === "canDrownInFluidType" &&
              String(m.desc) === "(" + fluidType + ")Z") {
            var code = new List();
            var vanilla = new Label();
            code.add(new Var(O.ALOAD, 0));
            code.add(new Type(O.CHECKCAST, "net/minecraft/world/entity/LivingEntity"));
            code.add(new Var(O.ALOAD, 1));
            code.add(invoke("de/codemorra/sheeper/world/ArenaWater", "allowsBreathing",
                "(" + living + fluidType + ")Z"));
            code.add(new Jump(O.IFEQ, vanilla));
            code.add(new Insn(O.ICONST_0));
            code.add(new Insn(O.IRETURN));
            code.add(vanilla);
            m.instructions.insert(code);
            breathing++;
          }
          if (String(m.name) === "jumpInFluid" && String(m.desc) === "(" + fluidType + ")V") {
            var instructions = m.instructions.toArray();
            for (var j = 0; j < instructions.length; j++) {
              if (instructions[j].getOpcode() !== O.RETURN) continue;
              var code = new List();
              code.add(new Var(O.ALOAD, 0));
              code.add(new Type(O.CHECKCAST, "net/minecraft/world/entity/LivingEntity"));
              code.add(new Var(O.ALOAD, 1));
              code.add(invoke("de/codemorra/sheeper/world/ArenaWater", "applyLavaSwimLift",
                  "(" + living + fluidType + ")V"));
              m.instructions.insertBefore(instructions[j], code);
              lavaLift++;
            }
          }
        }
        check(breathing, 1, "IForgeLivingEntity.canDrownInFluidType");
        check(lavaLift, 1, "IForgeLivingEntity.jumpInFluid");
        return node;
      }
    },
    arena_water_air_supply: {
      target: { type: "CLASS", name: "net.minecraft.world.entity.LivingEntity" },
      transformer: function (node) {
        var loss = 0;
        var recovery = 0;
        for (var i = 0; i < node.methods.size(); i++) {
          var method = node.methods.get(i);
          if (String(method.name) !== API.mapMethod("m_6075_") || String(method.desc) !== "()V") continue;
          var instructions = method.instructions.toArray();
          for (var j = 0; j < instructions.length; j++) {
            var insn = instructions[j];
            if (insn.getOpcode() !== O.INVOKEVIRTUAL || String(insn.desc) !== "(I)I") continue;
            var hook;
            if (String(insn.name) === API.mapMethod("m_7302_")) {
              loss++;
              hook = "adjustAirLoss";
            } else if (String(insn.name) === API.mapMethod("m_7305_")) {
              recovery++;
              hook = "adjustAirRecovery";
            } else {
              continue;
            }
            var code = new List();
            code.add(new Var(O.ALOAD, 0));
            code.add(invoke("de/codemorra/sheeper/world/ArenaWater", hook,
                "(ILnet/minecraft/world/entity/LivingEntity;)I"));
            method.instructions.insert(insn, code);
          }
        }
        check(loss, 1, "LivingEntity.baseTick air loss");
        check(recovery, 1, "LivingEntity.baseTick air recovery");
        return node;
      }
    },
    arena_water_fountain_lift: {
      target: { type: "CLASS", name: "net.minecraft.world.entity.LivingEntity" },
      transformer: function (node) {
        var count = 0;
        var vector = "Lnet/minecraft/world/phys/Vec3;";
        for (var i = 0; i < node.methods.size(); i++) {
          var method = node.methods.get(i);
          if (String(method.name) !== API.mapMethod("m_7023_") || String(method.desc) !== "(" + vector + ")V") continue;
          var instructions = method.instructions.toArray();
          for (var j = 0; j < instructions.length; j++) {
            var insn = instructions[j];
            if (insn.getOpcode() !== O.INVOKEVIRTUAL || String(insn.name) !== API.mapMethod("m_20994_") ||
                String(insn.desc) !== "(DZ" + vector + ")" + vector) continue;
            var code = new List();
            code.add(new Var(O.ALOAD, 0));
            code.add(invoke("de/codemorra/sheeper/world/ArenaWater", "applyAirFountainLift",
                "(" + vector + "Lnet/minecraft/world/entity/LivingEntity;)" + vector));
            method.instructions.insert(insn, code);
            count++;
          }
        }
        check(count, 2, "LivingEntity.travel fluid movement");
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
