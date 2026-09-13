package com.stonewu.fusion.entity.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.stonewu.fusion.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** Logical workflow capability metadata. The executable graph remains in ComfyUiWorkflowVersion. */
@TableName("afv_workflow_profile")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowProfile extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Stable logical profile identifier, for example WAN_I2V_STANDARD. */
    private String code;

    private String name;

    /** Existing logical workflow identity; this profile never copies the executable graph. */
    private Long workflowId;

    /** Capability purpose, for example I2V, FLF or INFINITETALK. */
    private String purpose;

    /** JSON metadata describing supported semantic capabilities. */
    private String capabilitiesJson;

    /** JSON semantic input contract; node IDs stay in the referenced workflow version. */
    private String inputContractJson;

    /** JSON semantic output contract. */
    private String outputContractJson;

    /** JSON list/object of custom nodes, model files and binary dependencies. */
    private String dependencyManifestJson;

    /** JSON runtime constraints such as CUDA, VRAM or backend requirements. */
    private String runtimeRequirementsJson;

    /** 0-disabled, 1-enabled. */
    @Builder.Default
    private Integer status = 1;

    @Builder.Default
    private Long deletedId = 0L;
}
