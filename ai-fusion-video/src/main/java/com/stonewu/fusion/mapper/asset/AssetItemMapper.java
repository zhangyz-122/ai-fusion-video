package com.stonewu.fusion.mapper.asset;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stonewu.fusion.entity.asset.AssetItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssetItemMapper extends BaseMapper<AssetItem> {

    /** 物理删除指定主资产下的全部子资产行（绕过逻辑删除），返回受影响行数 */
    @Delete("DELETE FROM afv_asset_item WHERE asset_id = #{assetId}")
    int deletePhysicallyByAssetId(@Param("assetId") Long assetId);
}
