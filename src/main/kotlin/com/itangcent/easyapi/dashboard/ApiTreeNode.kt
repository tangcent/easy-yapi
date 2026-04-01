package com.itangcent.easyapi.dashboard

import com.itangcent.easyapi.exporter.model.ApiEndpoint

/**
 * Tree node hierarchy for the API dashboard tree view.
 *
 * The tree structure represents:
 * - ModuleNode: Top-level grouping (e.g., by module or package)
 * - ClassNode: Controller/resource class grouping
 * - EndpointNode: Individual API endpoint
 *
 * ## Structure Example
 * ```
 * ModuleNode("user-service")
 *   └── ClassNode("UserController")
 *         ├── EndpointNode(GET /users)
 *         └── EndpointNode(POST /users)
 * ```
 */
sealed class ApiTreeNode {
    /**
     * Represents a module or package grouping in the tree.
     *
     * @param name The module/package name
     * @param children Child nodes (classes or endpoints)
     */
    data class ModuleNode(val name: String, val children: List<ApiTreeNode> = emptyList()) : ApiTreeNode()
    
    /**
     * Represents a controller/resource class in the tree.
     *
     * @param name The class name
     * @param description The class description
     * @param children Child endpoint nodes
     */
    data class ClassNode(val name: String, val description: String? = null, val children: List<ApiTreeNode> = emptyList()) : ApiTreeNode()
    
    /**
     * Represents an individual API endpoint in the tree.
     *
     * @param endpoint The API endpoint data
     */
    data class EndpointNode(val endpoint: ApiEndpoint) : ApiTreeNode()
}
