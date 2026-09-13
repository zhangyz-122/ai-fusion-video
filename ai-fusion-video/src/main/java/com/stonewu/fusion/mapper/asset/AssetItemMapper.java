package com.stonewu.fusion.mapper.asset;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stonewu.fusion.entity.asset.AssetItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AssetItemMapper extends BaseMapper<AssetItem> {

    /** 物理删除指定主资产下的全部子资产行（绕过逻辑删除），返回受影响行数 */
    @Delete("DELETE FROM afv_asset_item WHERE asset_id = #{assetId}")
    int deletePhysicallyByAssetId(@Param("assetId") Long assetId);

    /**
     * 恢复指定主资产下已逻辑删除的全部子资产行（置 deleted = 0），仅命中软删行。
     * <p>
     * 项目级联删除会连同子资产一起软删，回收站恢复主资产时需同步恢复，否则子资产永久不可达。
     */
    @Update("UPDATE afv_asset_item SET deleted = 0 WHERE asset_id = #{assetId} AND deleted = 1")
    int restoreDeletedByAssetId(@Param("assetId") Long assetId);

    /**
     * 查询指定主资产下的全部子资产行（含已逻辑删除行）。
     * <p>
     * BaseMapper 注入方法会被 @TableLogic 追加 deleted = 0 过滤，无法触达软删子资产，
     * 彻底删除前收集媒体文件地址必须走此显式 SQL。
     */
    @Select("SELECT * FROM afv_asset_item WHERE asset_id = #{assetId}")
    List<AssetItem> selectPhysicallyByAssetId(@Param("assetId") Long assetId);
}
