package com.itangcent.model.generic;

import com.itangcent.model.generic.AtaPageResult;
import com.itangcent.model.generic.VotePageQueryVO;

/**
 * Concrete result type: AtaPageResult<VotePageQueryVO>
 *
 * Full hierarchy:
 *   VotePageQueryResult extends AtaPageResult<VotePageQueryVO>
 *   AtaPageResult<T> extends AtaBaseResult<AtaPage<T>>
 *   AtaBaseResult<T> extends BaseResult<T>
 *   BaseResult<D> { D content; }
 *
 * So content should resolve to AtaPage<VotePageQueryVO>,
 * which has data: List<VotePageQueryVO>, totalCount, currentPage, pageSize, hasNextPage
 */
public class VotePageQueryResult extends AtaPageResult<VotePageQueryVO> {
}
