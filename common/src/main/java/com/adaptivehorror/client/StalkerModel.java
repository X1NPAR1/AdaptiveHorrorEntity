package com.adaptivehorror.client;

import com.adaptivehorror.entity.StalkerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/**
 * A featureless humanoid silhouette. Deliberately plain - the horror is in the stillness and the
 * colour (solid white by day, solid black by night), not in detail. Locomotion limbs never animate
 * because the entity never moves; the default humanoid pose is exactly the motionless stance wanted.
 */
public final class StalkerModel extends HumanoidModel<StalkerEntity> {

    public StalkerModel(ModelPart root) {
        super(root);
    }

    /** Standard humanoid mesh, baked into the {@link ModModelLayers#STALKER} layer at load time. */
    public static LayerDefinition createBodyLayer() {
        final MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(StalkerEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // Head tracks the player faintly; body stays rigid. No walk/idle animation.
        this.head.yRot = netHeadYaw * ((float) Math.PI / 180F) * 0.5F;
        this.head.xRot = headPitch * ((float) Math.PI / 180F) * 0.5F;
    }
}
