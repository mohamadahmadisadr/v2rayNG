package dev.sadr.atlas.contracts

import dev.sadr.atlas.dto.entities.ProfileItem

interface MainAdapterListener : BaseAdapterListener {

    fun onEdit(guid: String, position: Int, profile: ProfileItem)

    fun onSelectServer(guid: String)

    fun onShare(guid: String, profile: ProfileItem, position: Int, more: Boolean)

}