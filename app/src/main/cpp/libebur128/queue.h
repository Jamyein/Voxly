/*
 * Minimal BSD queue macros for libebur128
 * Based on BSD sys/queue.h - simplified implementation
 */

#ifndef _QUEUE_H_
#define _QUEUE_H_

/*
 * Singly-linked Tail queue declarations.
 */
#define STAILQ_HEAD(name, type)                                         \
    struct name {                                                        \
        struct type *stqh_first; /* first element */                     \
        struct type **stqh_last; /* addr of last next element */         \
    }

#define STAILQ_ENTRY(type)                                              \
    struct {                                                             \
        struct type *stqe_next;  /* next element */                      \
    }

/*
 * Singly-linked Tail queue functions.
 */
#define STAILQ_INIT(head) do {                                          \
    (head)->stqh_first = NULL;                                           \
    (head)->stqh_last = &(head)->stqh_first;                            \
} while (0)

#define STAILQ_INSERT_TAIL(head, elm, field) do {                       \
    (elm)->field.stqe_next = NULL;                                       \
    *(head)->stqh_last = (elm);                                          \
    (head)->stqh_last = &(elm)->field.stqe_next;                        \
} while (0)

#define STAILQ_REMOVE_HEAD(head, field) do {                            \
    if (((head)->stqh_first = (head)->stqh_first->field.stqe_next) == NULL) \
        (head)->stqh_last = &(head)->stqh_first;                        \
} while (0)

#define STAILQ_FOREACH(var, head, field)                                \
    for ((var) = (head)->stqh_first; (var); (var) = (var)->field.stqe_next)

#define STAILQ_FIRST(head)      ((head)->stqh_first)
#define STAILQ_EMPTY(head)      ((head)->stqh_first == NULL)

#endif /* _QUEUE_H_ */
