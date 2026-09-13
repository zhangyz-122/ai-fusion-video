-- Keep asset-item lookups efficient without sorting large JSON properties.
ALTER TABLE `afv_asset_item`
  ADD INDEX `idx_asset_item_asset_deleted_order` (`asset_id`, `deleted`, `sort_order`);
