create table if not exists event_store
(
    created_at   datetime(6)  not null,
    timestamp    datetime(6)  not null,
    version      bigint       not null,
    event_id     varchar(36)  not null
        primary key,
    aggregate_id varchar(100) not null,
    event_type   varchar(100) not null,
    metadata     text         null,
    payload      text         not null
);

create index idx_aggregate_id
    on event_store (aggregate_id);

create index idx_event_type
    on event_store (event_type);

create index idx_timestamp
    on event_store (timestamp);

create table if not exists paper
(
    impact_factor double       null,
    published_at  date         null,
    search_date   date         null,
    id            bigint auto_increment
        primary key,
    summarized_at timestamp(6) null,
    arxiv_id      varchar(32)  null,
    author        text         null,
    journal       varchar(255) null,
    novelty       varchar(255) null,
    summary       text         null,
    title         varchar(255) null,
    url           varchar(255) null
);

create table if not exists saga_event_store
(
    id              bigint auto_increment
        primary key,
    event_id        varchar(36)  not null,
    saga_id         varchar(36)  not null,
    sequence_number bigint       not null,
    event_type      varchar(255) null,
    timestamp       timestamp(6) not null,
    payload         text         not null,
    step_name       varchar(100) null,
    step_index      int          null,
    success         bit          not null,
    error_message   text         null,
    version         bigint       not null
);

create index idx_event_type
    on saga_event_store (event_type);

create index idx_saga_id
    on saga_event_store (saga_id);

create index idx_saga_id_sequence
    on saga_event_store (saga_id, sequence_number);

create index idx_timestamp
    on saga_event_store (timestamp);

create table if not exists saga_state
(
    saga_id            varchar(36)  not null
        primary key,
    saga_type          varchar(100) not null,
    status             varchar(30)  not null,
    current_step_index int          not null,
    total_steps        int          not null,
    saga_data          text         null,
    created_at         timestamp(6) not null,
    updated_at         timestamp(6) not null,
    version            bigint       not null
);

create index idx_saga_state_status
    on saga_state (status);

create index idx_saga_state_type
    on saga_state (saga_type);

create table if not exists subscribes
(
    activated      bit                                              not null,
    created_at     datetime(6)                                      null,
    id             bigint auto_increment
        primary key,
    updated_at     datetime(6)                                      null,
    description    varchar(255)                                     null,
    name           varchar(255)                                     not null,
    subscribe_type enum ('AUTHOR', 'CATEGORY', 'CUSTOM', 'KEYWORD') null,
    constraint UK3s9btd1qshtbr1bj99ub9hc7h
        unique (name)
);

create table if not exists paper_subscribes
(
    match_score  double      not null,
    id           bigint auto_increment
        primary key,
    matched_at   datetime(6) null,
    paper_id     bigint      not null,
    subscribe_id bigint      not null,
    match_reason text        null,
    constraint UKp8kq97enoork98gvta06wt0la
        unique (paper_id, subscribe_id),
    constraint FK6l4mbmdjv6us01f0hfdbo40f4
        foreign key (subscribe_id) references subscribes (id),
    constraint FKjrgvojio54moc4qniol920yht
        foreign key (paper_id) references paper (id)
);

create index idx_paper_id
    on paper_subscribes (paper_id);

create index idx_subscribe_id
    on paper_subscribes (subscribe_id);

create table if not exists users
(
    activated_at  datetime(6)                                                 null,
    birth         datetime(6)                                                 null,
    certified_at  datetime(6)                                                 null,
    created_at    datetime(6)                                                 null,
    deleted_at    datetime(6)                                                 null,
    id            bigint auto_increment
        primary key,
    release_at    datetime(6)                                                 null,
    updated_at    datetime(6)                                                 null,
    ci            varchar(255)                                                null,
    delete_reason varchar(255)                                                null,
    email         varchar(255)                                                null,
    identity      varchar(255)                                                null,
    migration_id  varchar(255)                                                null,
    name          varchar(255)                                                null,
    note          varchar(255)                                                null,
    password      varchar(255)                                                null,
    phone_number  varchar(255)                                                null,
    gender        enum ('FEMALE', 'MALE', 'UNKNOWN')                          null,
    status        enum ('ACTIVE', 'DELETED', 'INACTIVE', 'INIT', 'SUSPENDED') null
);

create table if not exists oauth_providers
(
    created_at  datetime(6)                                null,
    id          bigint auto_increment
        primary key,
    updated_at  datetime(6)                                null,
    user_id     bigint                                     null,
    provider_id varchar(255)                               null,
    kind        enum ('APPLE', 'GOOGLE', 'KAKAO', 'NAVER') null,
    constraint FKjm6xdbkpwi6ejep2u3rh3nlri
        foreign key (user_id) references users (id)
);

create table if not exists user_subscribes
(
    notification_enabled bit         not null,
    priority             int         not null,
    id                   bigint auto_increment
        primary key,
    subscribe_id         bigint      not null,
    subscribed_at        datetime(6) null,
    unsubscribed_at      datetime(6) null,
    user_id              bigint      not null,
    constraint UK878x9yfk8d4de4vy3qkmcelhx
        unique (user_id, subscribe_id),
    constraint FK4dw17ix5n0rd5gamdv3w85f2r
        foreign key (subscribe_id) references subscribes (id),
    constraint FK94gl6xya1xt02uxejionn8p80
        foreign key (user_id) references users (id)
);