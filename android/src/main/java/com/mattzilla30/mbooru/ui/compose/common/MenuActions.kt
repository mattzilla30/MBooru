// Copyright (C) 2020 onlymash <fiepi.dev@gmail.com>; 2026 mattzilla30 <mattzilla30@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.compose.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mattzilla30.mbooru.R

enum class AppActionKey {
    AccountRemove,
    ArtistDanGroup0,
    ArtistMoeGroup0,
    ArtistOrderCount,
    ArtistOrderDate,
    ArtistOrderName,
    Back,
    BooruAddClipboard,
    BooruAddFeatured,
    BooruAddManual,
    BooruAddQr,
    BooruBackupToFile,
    BooruConfigApply,
    BooruConfigDelete,
    BooruDelete,
    BooruEdit,
    BooruGroup0,
    BooruGroup1,
    BooruRestoreFromFile,
    BooruShareClipboard,
    BooruShareQrCode,
    BrowseComment,
    BrowseOpenBrowser,
    BrowseSend,
    BrowseSetAs,
    BrowseShare,
    CommentDelete,
    CommentQuote,
    CommentReply,
    Date,
    DateRange,
    Day,
    ExpandOrClear,
    HistoryClearAll,
    Month,
    MuzeiFetch,
    MuzeiItemCopy,
    MuzeiItemDelete,
    MuzeiItemSearch,
    MuzeiSearch,
    NavArtists,
    NavPools,
    NavPopular,
    NavPosts,
    NavTags,
    OptionFile,
    OptionMain,
    OptionUrl,
    SauceNaoChangeApiKey,
    SauceNaoGetApiKey,
    SauceNaoGroup0,
    TagBlacklistItemCopy,
    TagBlacklistItemDelete,
    TagDanGroup0,
    TagDanGroup1,
    TagDanOneGroup0,
    TagDanOneGroup1,
    TagGelGroup0,
    TagMoeGroup0,
    TagMoeGroup1,
    TagOrderCount,
    TagOrderDate,
    TagOrderName,
    TagSankakuGroup0,
    TagSankakuGroup1,
    TagTypeAll,
    TagTypeArtist,
    TagTypeCharacter,
    TagTypeCircle,
    TagTypeCopyright,
    TagTypeFaults,
    TagTypeGeneral,
    TagTypeGenre,
    TagTypeMedium,
    TagTypeMeta,
    TagTypeMetaSankaku,
    TagTypeModel,
    TagTypePhotoSet,
    TagTypeStudio,
    Week,
}

enum class AppMenu {
    None,
    Account,
    ArtistDan,
    ArtistMoe,
    Booru,
    BooruActionMenu,
    BooruConfig,
    Comment,
    CommentItem,
    CommentItemMe,
    Detail,
    DetailShimmie,
    History,
    Muzei,
    MuzeiItem,
    Navigation,
    NavigationGel,
    NavigationSankaku,
    NavigationShimmie,
    PopularDan,
    PopularSankaku,
    Post,
    SauceNao,
    SauceNaoFab,
    TagBlacklistItem,
    TagDan,
    TagDanOne,
    TagGel,
    TagMoe,
    TagSankaku,
}

data class AppAction(
    val key: AppActionKey,
    @StringRes val title: Int,
    @DrawableRes val icon: Int = 0,
    val direct: Boolean = false,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val children: List<AppAction> = emptyList(),
    val shortcut: Char? = null,
)

fun AppMenu.actions(): List<AppAction> =
    when (this) {
        AppMenu.None -> emptyList()
        AppMenu.Account ->
            listOf(
                AppAction(
                    AppActionKey.AccountRemove,
                    R.string.account_user_remove,
                    icon = 0,
                    direct = false,
                )
            )
        AppMenu.ArtistDan ->
            listOf(
                AppAction(
                    AppActionKey.ArtistDanGroup0,
                    R.string.artist_order,
                    icon = R.drawable.ic_sort_24dp,
                    direct = true,
                    children =
                        listOf(
                            AppAction(
                                AppActionKey.ArtistOrderDate,
                                R.string.artist_order_date,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.ArtistOrderName,
                                R.string.artist_order_name,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.ArtistOrderCount,
                                R.string.artist_order_count,
                                icon = 0,
                                direct = false,
                            ),
                        ),
                )
            )
        AppMenu.ArtistMoe ->
            listOf(
                AppAction(
                    AppActionKey.ArtistMoeGroup0,
                    R.string.artist_order,
                    icon = R.drawable.ic_sort_24dp,
                    direct = true,
                    children =
                        listOf(
                            AppAction(
                                AppActionKey.ArtistOrderDate,
                                R.string.artist_order_date,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.ArtistOrderName,
                                R.string.artist_order_name,
                                icon = 0,
                                direct = false,
                            ),
                        ),
                )
            )
        AppMenu.Booru ->
            listOf(
                AppAction(
                    AppActionKey.BooruGroup0,
                    R.string.booru_add,
                    icon = R.drawable.ic_note_add_outline_24dp,
                    direct = true,
                    children =
                        listOf(
                            AppAction(
                                AppActionKey.BooruAddFeatured,
                                R.string.booru_add_featured,
                                icon = R.drawable.ic_star_border_24dp,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.BooruAddQr,
                                R.string.booru_add_from_qr_code,
                                icon = R.drawable.ic_camera_alt_outline_24dp,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.BooruAddClipboard,
                                R.string.booru_add_from_clipboard,
                                icon = R.drawable.ic_input_outline_24dp,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.BooruAddManual,
                                R.string.booru_add_from_manual_settings,
                                icon = R.drawable.ic_build_outline_24dp,
                                direct = false,
                            ),
                        ),
                ),
                AppAction(
                    AppActionKey.BooruGroup1,
                    R.string.booru_backup_or_restore,
                    icon = R.drawable.ic_restore_page_outline_24dp,
                    direct = true,
                    children =
                        listOf(
                            AppAction(
                                AppActionKey.BooruBackupToFile,
                                R.string.booru_backup_to_file,
                                icon = R.drawable.ic_create_new_folder_outline_24dp,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.BooruRestoreFromFile,
                                R.string.booru_restore_from_file,
                                icon = R.drawable.ic_folder_open_outline_24dp,
                                direct = false,
                            ),
                        ),
                ),
            )
        AppMenu.BooruActionMenu ->
            listOf(
                AppAction(
                    AppActionKey.BooruShareQrCode,
                    R.string.booru_share_by_qr_code,
                    icon = 0,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.BooruShareClipboard,
                    R.string.booru_share_by_clipboard,
                    icon = 0,
                    direct = false,
                ),
                AppAction(AppActionKey.BooruEdit, R.string.booru_edit, icon = 0, direct = false),
                AppAction(
                    AppActionKey.BooruDelete,
                    R.string.booru_delete,
                    icon = 0,
                    direct = false,
                ),
            )
        AppMenu.BooruConfig ->
            listOf(
                AppAction(
                    AppActionKey.BooruConfigDelete,
                    R.string.booru_config_delete,
                    icon = R.drawable.ic_delete_24dp,
                    direct = true,
                    shortcut = 'd',
                ),
                AppAction(
                    AppActionKey.BooruConfigApply,
                    R.string.booru_config_apply,
                    icon = R.drawable.ic_done_24dp,
                    direct = true,
                    shortcut = 's',
                ),
            )
        AppMenu.Comment ->
            listOf(
                AppAction(
                    AppActionKey.CommentReply,
                    R.string.comment_reply,
                    icon = R.drawable.ic_add_comment_outline_24dp,
                    direct = true,
                )
            )
        AppMenu.CommentItem ->
            listOf(
                AppAction(
                    AppActionKey.CommentReply,
                    R.string.comment_reply,
                    icon = 0,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.CommentQuote,
                    R.string.comment_quote,
                    icon = 0,
                    direct = false,
                ),
            )
        AppMenu.CommentItemMe ->
            listOf(
                AppAction(
                    AppActionKey.CommentReply,
                    R.string.comment_reply,
                    icon = 0,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.CommentQuote,
                    R.string.comment_quote,
                    icon = 0,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.CommentDelete,
                    R.string.comment_delete,
                    icon = 0,
                    direct = false,
                ),
            )
        AppMenu.Detail ->
            listOf(
                AppAction(
                    AppActionKey.BrowseComment,
                    R.string.browse_comment,
                    icon = R.drawable.ic_mode_comment_outline_24dp,
                    direct = true,
                ),
                AppAction(
                    AppActionKey.BrowseSetAs,
                    R.string.browse_set_as,
                    icon = 0,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.BrowseShare,
                    R.string.browse_share_link,
                    icon = 0,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.BrowseSend,
                    R.string.browse_send_file,
                    icon = 0,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.BrowseOpenBrowser,
                    R.string.browse_open_in_browser,
                    icon = 0,
                    direct = false,
                ),
            )
        AppMenu.DetailShimmie ->
            listOf(
                AppAction(
                    AppActionKey.BrowseSetAs,
                    R.string.browse_set_as,
                    icon = 0,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.BrowseShare,
                    R.string.browse_share_link,
                    icon = 0,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.BrowseSend,
                    R.string.browse_send_file,
                    icon = 0,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.BrowseOpenBrowser,
                    R.string.browse_open_in_browser,
                    icon = 0,
                    direct = false,
                ),
            )
        AppMenu.History ->
            listOf(
                AppAction(
                    AppActionKey.HistoryClearAll,
                    R.string.history_clear_all,
                    icon = R.drawable.ic_baseline_clear_all_24,
                    direct = true,
                )
            )
        AppMenu.Muzei ->
            listOf(
                AppAction(
                    AppActionKey.MuzeiSearch,
                    R.string.muzei_search,
                    icon = R.drawable.ic_search_24dp,
                    direct = true,
                ),
                AppAction(
                    AppActionKey.MuzeiFetch,
                    R.string.muzei_fetch,
                    icon = R.drawable.ic_refresh_24dp,
                    direct = false,
                ),
            )
        AppMenu.MuzeiItem ->
            listOf(
                AppAction(
                    AppActionKey.MuzeiItemSearch,
                    R.string.muzei_search,
                    icon = 0,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.MuzeiItemCopy,
                    R.string.muzei_copy,
                    icon = 0,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.MuzeiItemDelete,
                    R.string.muzei_delete,
                    icon = 0,
                    direct = false,
                ),
            )
        AppMenu.Navigation ->
            listOf(
                AppAction(
                    AppActionKey.NavPosts,
                    R.string.title_posts,
                    icon = R.drawable.ic_state_photo,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.NavPopular,
                    R.string.title_popular,
                    icon = R.drawable.ic_state_whatshot,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.NavPools,
                    R.string.title_pools,
                    icon = R.drawable.ic_state_photo_album,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.NavTags,
                    R.string.title_tags,
                    icon = R.drawable.ic_state_bookmark,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.NavArtists,
                    R.string.title_artists,
                    icon = R.drawable.ic_state_people,
                    direct = false,
                ),
            )
        AppMenu.NavigationGel ->
            listOf(
                AppAction(
                    AppActionKey.NavPosts,
                    R.string.title_posts,
                    icon = R.drawable.ic_state_photo,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.NavTags,
                    R.string.title_tags,
                    icon = R.drawable.ic_state_bookmark,
                    direct = false,
                ),
            )
        AppMenu.NavigationSankaku ->
            listOf(
                AppAction(
                    AppActionKey.NavPosts,
                    R.string.title_posts,
                    icon = R.drawable.ic_state_photo,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.NavPopular,
                    R.string.title_popular,
                    icon = R.drawable.ic_state_whatshot,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.NavPools,
                    R.string.title_pools,
                    icon = R.drawable.ic_state_photo_album,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.NavTags,
                    R.string.title_tags,
                    icon = R.drawable.ic_state_bookmark,
                    direct = false,
                ),
            )
        AppMenu.NavigationShimmie ->
            listOf(
                AppAction(
                    AppActionKey.NavPosts,
                    R.string.title_posts,
                    icon = R.drawable.ic_state_photo,
                    direct = false,
                )
            )
        AppMenu.PopularDan ->
            listOf(
                AppAction(
                    AppActionKey.Date,
                    R.string.popular_date,
                    icon = R.drawable.ic_event_24dp,
                    direct = true,
                ),
                AppAction(
                    AppActionKey.DateRange,
                    R.string.popular_date_range,
                    icon = R.drawable.ic_event_note_24dp,
                    direct = true,
                    children =
                        listOf(
                            AppAction(
                                AppActionKey.Day,
                                R.string.popular_day,
                                icon = R.drawable.ic_today_outline_24dp,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.Week,
                                R.string.popular_week,
                                icon = R.drawable.ic_view_week_outline_24dp,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.Month,
                                R.string.popular_month,
                                icon = R.drawable.ic_event_note_24dp,
                                direct = false,
                            ),
                        ),
                ),
            )
        AppMenu.PopularSankaku ->
            listOf(
                AppAction(
                    AppActionKey.DateRange,
                    R.string.popular_date_range,
                    icon = R.drawable.ic_date_range_24dp,
                    direct = true,
                )
            )
        AppMenu.Post ->
            listOf(
                AppAction(
                    AppActionKey.ExpandOrClear,
                    R.string.post_expand_or_clear,
                    icon = R.drawable.ic_add_long_24dp,
                    direct = true,
                )
            )
        AppMenu.SauceNao ->
            listOf(
                AppAction(
                    AppActionKey.SauceNaoGroup0,
                    R.string.sauce_nao_help,
                    icon = R.drawable.ic_help_outline_24dp,
                    direct = true,
                    children =
                        listOf(
                            AppAction(
                                AppActionKey.SauceNaoChangeApiKey,
                                R.string.sauce_nao_change_api_key,
                                icon = R.drawable.ic_edit_24dp,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.SauceNaoGetApiKey,
                                R.string.sauce_nao_get_api_key,
                                icon = R.drawable.ic_open_in_new_24dp,
                                direct = false,
                            ),
                        ),
                )
            )
        AppMenu.SauceNaoFab ->
            listOf(
                AppAction(
                    AppActionKey.OptionMain,
                    0,
                    icon = R.drawable.ic_add_long_white_24dp,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.OptionUrl,
                    R.string.sauce_nao_search_from_url,
                    icon = R.drawable.ic_link_24dp,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.OptionFile,
                    R.string.sauce_nao_search_from_file,
                    icon = R.drawable.ic_attach_file_24dp,
                    direct = false,
                ),
            )
        AppMenu.TagBlacklistItem ->
            listOf(
                AppAction(
                    AppActionKey.TagBlacklistItemCopy,
                    R.string.tag_blacklist_copy,
                    icon = 0,
                    direct = false,
                ),
                AppAction(
                    AppActionKey.TagBlacklistItemDelete,
                    R.string.tag_blacklist_delete,
                    icon = 0,
                    direct = false,
                ),
            )
        AppMenu.TagDan ->
            listOf(
                AppAction(
                    AppActionKey.TagDanGroup0,
                    R.string.tag_type,
                    icon = R.drawable.ic_filter_list_24dp,
                    direct = true,
                    children =
                        listOf(
                            AppAction(
                                AppActionKey.TagTypeAll,
                                R.string.tag_type_all,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeGeneral,
                                R.string.tag_type_general,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeArtist,
                                R.string.tag_type_artist,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeCopyright,
                                R.string.tag_type_copyright,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeCharacter,
                                R.string.tag_type_character,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeMeta,
                                R.string.tag_type_meta,
                                icon = 0,
                                direct = false,
                            ),
                        ),
                ),
                AppAction(
                    AppActionKey.TagDanGroup1,
                    R.string.tag_order,
                    icon = R.drawable.ic_sort_24dp,
                    direct = true,
                    children =
                        listOf(
                            AppAction(
                                AppActionKey.TagOrderDate,
                                R.string.tag_order_date,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagOrderName,
                                R.string.tag_order_name,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagOrderCount,
                                R.string.tag_order_count,
                                icon = 0,
                                direct = false,
                            ),
                        ),
                ),
            )
        AppMenu.TagDanOne ->
            listOf(
                AppAction(
                    AppActionKey.TagDanOneGroup0,
                    R.string.tag_type,
                    icon = R.drawable.ic_filter_list_24dp,
                    direct = true,
                    children =
                        listOf(
                            AppAction(
                                AppActionKey.TagTypeAll,
                                R.string.tag_type_all,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeGeneral,
                                R.string.tag_type_general,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeArtist,
                                R.string.tag_type_artist,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeCopyright,
                                R.string.tag_type_copyright,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeCharacter,
                                R.string.tag_type_character,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeModel,
                                R.string.tag_type_model,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypePhotoSet,
                                R.string.tag_type_photo_set,
                                icon = 0,
                                direct = false,
                            ),
                        ),
                ),
                AppAction(
                    AppActionKey.TagDanOneGroup1,
                    R.string.tag_order,
                    icon = R.drawable.ic_sort_24dp,
                    direct = true,
                    children =
                        listOf(
                            AppAction(
                                AppActionKey.TagOrderDate,
                                R.string.tag_order_date,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagOrderName,
                                R.string.tag_order_name,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagOrderCount,
                                R.string.tag_order_count,
                                icon = 0,
                                direct = false,
                            ),
                        ),
                ),
            )
        AppMenu.TagGel ->
            listOf(
                AppAction(
                    AppActionKey.TagGelGroup0,
                    R.string.tag_order,
                    icon = R.drawable.ic_sort_24dp,
                    direct = true,
                    children =
                        listOf(
                            AppAction(
                                AppActionKey.TagOrderDate,
                                R.string.tag_order_date,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagOrderName,
                                R.string.tag_order_name,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagOrderCount,
                                R.string.tag_order_count,
                                icon = 0,
                                direct = false,
                            ),
                        ),
                )
            )
        AppMenu.TagMoe ->
            listOf(
                AppAction(
                    AppActionKey.TagMoeGroup0,
                    R.string.tag_type,
                    icon = R.drawable.ic_filter_list_24dp,
                    direct = true,
                    children =
                        listOf(
                            AppAction(
                                AppActionKey.TagTypeAll,
                                R.string.tag_type_all,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeGeneral,
                                R.string.tag_type_general,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeArtist,
                                R.string.tag_type_artist,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeCopyright,
                                R.string.tag_type_copyright,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeCharacter,
                                R.string.tag_type_character,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeCircle,
                                R.string.tag_type_circle,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeFaults,
                                R.string.tag_type_faults,
                                icon = 0,
                                direct = false,
                            ),
                        ),
                ),
                AppAction(
                    AppActionKey.TagMoeGroup1,
                    R.string.tag_order,
                    icon = R.drawable.ic_sort_24dp,
                    direct = true,
                    children =
                        listOf(
                            AppAction(
                                AppActionKey.TagOrderDate,
                                R.string.tag_order_date,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagOrderName,
                                R.string.tag_order_name,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagOrderCount,
                                R.string.tag_order_count,
                                icon = 0,
                                direct = false,
                            ),
                        ),
                ),
            )
        AppMenu.TagSankaku ->
            listOf(
                AppAction(
                    AppActionKey.TagSankakuGroup0,
                    R.string.tag_type,
                    icon = R.drawable.ic_filter_list_24dp,
                    direct = true,
                    children =
                        listOf(
                            AppAction(
                                AppActionKey.TagTypeAll,
                                R.string.tag_type_all,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeGeneral,
                                R.string.tag_type_general,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeArtist,
                                R.string.tag_type_artist,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeCopyright,
                                R.string.tag_type_copyright,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeCharacter,
                                R.string.tag_type_character,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeMetaSankaku,
                                R.string.tag_type_meta,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeGenre,
                                R.string.tag_type_genre,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeMedium,
                                R.string.tag_type_medium,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagTypeStudio,
                                R.string.tag_type_studio,
                                icon = 0,
                                direct = false,
                            ),
                        ),
                ),
                AppAction(
                    AppActionKey.TagSankakuGroup1,
                    R.string.tag_order,
                    icon = R.drawable.ic_sort_24dp,
                    direct = true,
                    children =
                        listOf(
                            AppAction(
                                AppActionKey.TagOrderDate,
                                R.string.tag_order_date,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagOrderName,
                                R.string.tag_order_name,
                                icon = 0,
                                direct = false,
                            ),
                            AppAction(
                                AppActionKey.TagOrderCount,
                                R.string.tag_order_count,
                                icon = 0,
                                direct = false,
                            ),
                        ),
                ),
            )
    }
