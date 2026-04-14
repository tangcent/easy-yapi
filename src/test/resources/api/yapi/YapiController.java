package com.itangcent.yapi;

import com.itangcent.yapi.ItemDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/yapi")
public class YapiController {

    /**
     * Get public info
     *
     * @open
     * @return Returns public information
     */
    @GetMapping("/public")
    public String getPublicInfo() {
        return "public info";
    }

    /**
     * Get private info
     *
     * @return Returns private information
     */
    @GetMapping("/private")
    public String getPrivateInfo() {
        return "private info";
    }

    /**
     * Create item
     *
     * @undone
     * @param item the item to create
     * @return Created item
     */
    @PostMapping("/create")
    public ItemDTO createItem(@RequestBody ItemDTO item) {
        return item;
    }

    /**
     * Update item
     *
     * @todo
     * @param item the item to update
     * @return Updated item
     */
    @PostMapping("/update")
    public ItemDTO updateItem(@RequestBody ItemDTO item) {
        return item;
    }

    /**
     * Delete item
     *
     * @open
     * @undone
     * @param id the item id
     * @return Success message
     */
    @GetMapping("/delete/{id}")
    public String deleteItem(Long id) {
        return "deleted";
    }
}
