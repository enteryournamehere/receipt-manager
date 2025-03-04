package zip.zaop.paylink.network

import kotlinx.serialization.Serializable
import zip.zaop.paylink.database.DatabaseWbwList
import zip.zaop.paylink.database.DatabaseWbwMember

@Serializable
data class LoginRequest (
    val user: User
)

@Serializable
data class User (
    val email: String,
    val password: String,
)

@Serializable
data class Pagination(
    val total_pages: Int,
    val offset: Int,
    val per_page: Int,
    val total_entries: Int,
    val current_page: Int
)

@Serializable
data class Sorting(
    val fields: List<Field>,
    val sortable_fields: List<String>
)

@Serializable
data class Field(
    val modified_at: String?,
    val updated_at: String?,
)

@Serializable
data class PaginationResponse<Thing>(
    val pagination: Pagination,
    val sorting: Sorting,
    val filter: Map<String, String>, // e.g. "member_set": "available"
    val permissions: PermissionSet,
    val data: List<Thing>
)

@Serializable
data class NoPaginationResponse<Thing>(
    val permissions: PermissionSet,
    val data: List<Thing>
)

@Serializable
data class ListPermissions(
    val read: Boolean,
    val update: Boolean,
    val delete: Boolean,
    val image: ImagePermissions,
    val settles: PermissionSet,
    val list_items: PermissionSet
)

@Serializable
data class ImagePermissions(
    val permissions: PermissionSet
)

@Serializable
data class PermissionSet(
    val read: Boolean?,
    val update: Boolean?,
    val delete: Boolean?,
    val index: Boolean?,
    val create: Boolean?,
    val avatar: PermissionSet?,
    val invitation: PermissionSet?,
)

@Serializable
data class ImageUrls(
    // all null or all string
    val original: String?,
    val large: String?,
    val medium: String?,
    val small: String?
)

@Serializable
data class Image(
    val image: ImageUrls
)

@Serializable
data class ShortMemberInfo(
    val id: String,
    val user_id: String?,
    val nickname: String
)

@Serializable
data class WeirdMemberInfo(
    val id: String,
//    val user_id: List (1 string and then 1 int),
    val nickname: String
)

@Serializable
data class ListItem(
    val id: String,
    val description: String
)

@Serializable
data class LatestActivity(
    val activity: Activity
)

@Serializable
data class Activity(
    val id: String,
    val data: ActivityData,
    val type: String,
    val created_at: Long,
    val updated_at: Long
)

@Serializable
data class ActivityData(
    val actor: ShortMemberInfo?,
    val list_item: ListItem?,
    val payer: ShortMemberInfo?,
    val member: WeirdMemberInfo?,
    val amount: Amount?,
)

@Serializable
data class ListData(
    val id: String,
    val name: String,
    val currency: String,
    val created_at: Long,
    val updated_at: Long,
    val modified_at: Long,
    val settles_count: Int,
    val image: Image,
    val latest_activity: LatestActivity?
)

@Serializable
data class ListListResponseItem(
    val permissions: ListPermissions,
    val list: ListData
)

/// === get balances

@Serializable
data class Balance(
    val list: ListInfo,
    val member: MemberReduced,
    val amount: Amount,
    val created_at: Long,
    val updated_at: Long
)

@Serializable
data class ListInfo(
    val id: String,
    val name: String
)

@Serializable
data class MemberReduced(
    val id: String,
    val nickname: String
)

@Serializable
data class Amount(
    val currency: String,
    val fractional: Int
)

@Serializable
data class BalancesResponseItem(
    val balance: Balance
)

// === get list members
@Serializable
data class Member(
    val id: String,
    val nickname: String,
    val email: String,
    val list_id: String,
    val user_id: String,
    val full_name: String,
    val commitments_to_pay: Int,
    val commitments_to_receive: Int,
    val created_at: Long,
    val updated_at: Long,
    val deleted_at: Long?,
    val avatar: Avatar,
    val invitation: Invitation
)

@Serializable
data class Avatar(
    val image: ImageUrls?,
    val initials: String
)

@Serializable
data class Invitation(
    val invitation: InvitationData
)

@Serializable
data class InvitationData(
    val state: String,
    val url: String?
)

@Serializable
data class ListMemberListResponseItem(
    val permissions: PermissionSet,
    val member: Member,
)

// === login response
@Serializable
data class CurrentUser(
    val id: String,
    val email: String,
    val first_name: String,
    val last_name: String,
    val state: String,
    val locale: String,
    val city: String?,
    val unconfirmed_email: String?,
    val subscribed_for_updates: Boolean,
    val intent_url: String?,
    val created_at: Long,
    val updated_at: Long,
    val avatar: Avatar,
    val terms_accepted: Boolean,
    val payment_onboarding_last_shown_at: Long?,
    val payment_onboarding_times_viewed: Int,
    val features: Features,
    val totals: Totals,
    val intent_url_updated_at: Long?
)

@Serializable
data class Features(
    val single_payments: Feature,
    val ads: Feature,
    val premium: Feature,
    val exports: Feature,
    val search_and_filter: Feature,
    val performance_log: Feature
)

@Serializable
data class Feature(
    val min_version: String,
    val active: Boolean
)

@Serializable
data class Totals(
    val payables: Payables
)

@Serializable
data class Payables(
    val last_viewed_at: Long,
    val created_since_last_view: Int
)

@Serializable
data class LoginResponse(
    val permissions: PermissionSet?, // not ENTIRELY clear bc this always has avatar perms set.idk
    val current_user: CurrentUser?,
)

@Serializable
data class LoginErrorResponse(
    val errors: Errors,
    val message: String
)

@Serializable
data class Errors(
    val base: List<String>,
    val sign_in_error: List<String>
)

// == new expense
@Serializable
data class NewExpenseWrapper(
    val expense: Expense,
)

@Serializable
data class Expense(
    val id: String,
    val name: String,
    val payed_by_id: String,
    val payed_on: String,
    val source_amount: Amount,
    val amount: Amount,
    val exchange_rate: Int,
    val shares_attributes: List<ShareInfo>
)

@Serializable
data class ShareInfo(
    val id: String,
    val member_id: String,
    val source_amount: Amount,
    val amount: Amount,
    val meta: ShareMeta
)

@Serializable
data class ShareMeta(
    val type: String,
    val multiplier: Int
)

// the response
@Serializable
data class NewExpenseResponse(
    val permissions: PermissionSet?,
    val expense: ExpenseResponse?,
    val errors: Errors?,
    val message: String?
)

@Serializable
data class ExpenseResponse(
    val id: String,
    val name: String,
    val list_id: String,
    val settle_id: String?,
    val payed_by_member_instance_id: String?,
    val status: String,
    val payed_on: String,
    val exchange_rate: String,
    val payed_by_id: String,
    val category: Category?,
    val created_at: Int,
    val updated_at: Int,
    val source_amount: Amount,
    val amount: Amount,
    val shares: List<ShareWrapper>,
    val image: Image
)

@Serializable
data class Category(
    val id: Int,
    val sub_id: Int,
    val main_id: Int,
    val icon: String,
    // category_source: null, don't know what else it might be
    val main_description: String,
    val sub_description: String
)

@Serializable
data class ShareWrapper(
    val share: Share,
)

@Serializable
data class Share(
    val id: String,
    val meta: ShareMeta,
    val member_instance_id: String?,
    val member_id: String,
    val member_instance: String?,
    val source_amount: Amount,
    val amount: Amount
)

fun PaginationResponse<ListListResponseItem>.asDatabaseModel(): List<DatabaseWbwList> {
    return data.map {
        DatabaseWbwList(
            id = it.list.id,
            name = it.list.name,
            image_url = it.list.image.image.small,
            our_member_id = null,
        )
    }
}

fun List<ListMemberListResponseItem>.toDatabaseModel(): List<DatabaseWbwMember> {
    return this.map {
        DatabaseWbwMember(
            id = it.member.id,
            full_name = it.member.full_name,
            nickname = it.member.nickname,
            avatar_url = it.member.avatar.image?.medium,
            list_id = it.member.list_id,
        )
    }
}