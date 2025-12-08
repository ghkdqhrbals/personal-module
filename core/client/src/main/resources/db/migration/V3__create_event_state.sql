create table if not exists saga_state (
                            saga_id varchar(36) not null,
                            saga_type varchar(100) not null,
                            status varchar(30) not null,
                            current_step_index int not null,
                            total_steps int not null,
                            saga_data text,
                            created_at timestamp(6) not null,
                            updated_at timestamp(6) not null,
                            version bigint not null,
                            primary key (saga_id)
);

create index idx_saga_state_status on saga_state (status);
create index idx_saga_state_type on saga_state (saga_type);

create table saga_event_store (
                                  id bigint not null auto_increment,
                                  event_id varchar(36) not null,
                                  saga_id varchar(36) not null,
                                  sequence_number bigint not null,
                                  event_type varchar(255),
                                  timestamp timestamp(6) not null,
                                  payload text not null,
                                  step_name varchar(100),
                                  step_index int,
                                  success bit not null,
                                  error_message text,
                                  version bigint not null,
                                  primary key (id)
);

create index idx_saga_id on saga_event_store (saga_id);

create index idx_saga_id_sequence on saga_event_store (saga_id, sequence_number);

create index idx_event_type on saga_event_store (event_type);

create index idx_timestamp on saga_event_store (timestamp);