package com.adaptivehorror.client;

import com.adaptivehorror.entity.NullBossEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/**
 * The boss shares the featureless humanoid silhouette of the stalker, just rendered larger and always
 * black. Limbs animate normally here (unlike the stalker) because the boss actually walks.
 */
public final class NullBossModel extends HumanoidModel<NullBossEntity> {

    public NullBossModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        return LayerDefinition.create(mesh, 64, 64);
    }
}
