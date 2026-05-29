create table if not exists business_area (
    area_id varchar(64) primary key,
    city varchar(64) not null,
    district varchar(64) not null,
    area_name varchar(128) not null,
    center_lat double precision not null,
    center_lng double precision not null,
    coordinate_system varchar(32) not null,
    area_tags jsonb not null,
    suitable_scenes jsonb not null
);

create table if not exists traffic_matrix (
    from_area_id varchar(64) not null,
    to_area_id varchar(64) not null,
    from_area varchar(128) not null,
    to_area varchar(128) not null,
    transport_mode varchar(32) not null,
    distance_km double precision not null,
    estimated_minutes double precision not null,
    primary key (from_area_id, to_area_id, transport_mode)
);

create table if not exists poi_basic (
    poi_id varchar(64) primary key,
    business_area_id varchar(64),
    name varchar(255) not null,
    city varchar(64) not null,
    district varchar(64) not null,
    business_area varchar(128) not null,
    address varchar(255) not null,
    lat double precision not null,
    lng double precision not null,
    coordinate_system varchar(32) not null,
    category_lv1 varchar(64) not null,
    category_lv2 varchar(64) not null,
    brand varchar(128),
    branch_name varchar(128),
    status varchar(32) not null
);

create table if not exists poi_rating_stats (
    poi_id varchar(64) primary key,
    rating double precision not null,
    taste_score double precision not null,
    environment_score double precision not null,
    service_score double precision not null,
    review_count integer not null,
    favorite_count integer not null,
    popularity_score double precision not null,
    rank_desc varchar(255)
);

create table if not exists poi_business_info (
    poi_id varchar(64) primary key,
    avg_price integer not null,
    business_hours varchar(255) not null,
    reservation_available boolean not null,
    queue_supported boolean not null,
    avg_queue_minutes integer not null,
    has_group_buy boolean not null,
    coupon_desc varchar(255)
);

create table if not exists poi_ugc_summary (
    poi_id varchar(64) primary key,
    positive_keywords jsonb not null,
    negative_keywords jsonb not null,
    crowd_keywords jsonb not null,
    scene_keywords jsonb not null,
    review_summary text,
    avoid_reason text,
    recommend_reason text
);

create table if not exists poi_route_profile (
    poi_id varchar(64) primary key,
    route_roles jsonb not null,
    suitable_scenes jsonb not null,
    suitable_time_periods jsonb not null,
    avg_stay_minutes integer not null,
    indoor_outdoor varchar(32) not null,
    weather_sensitive boolean not null,
    energy_level varchar(32) not null,
    noise_level varchar(32) not null,
    photo_friendly boolean not null,
    family_friendly boolean not null,
    route_score double precision not null
);

create table if not exists poi_tag (
    poi_id varchar(64) not null,
    tag_type varchar(64) not null,
    tag_value varchar(128) not null,
    confidence double precision not null,
    source varchar(64) not null,
    primary key (poi_id, tag_type, tag_value, source)
);

create table if not exists poi_embedding_doc (
    poi_id varchar(64) primary key,
    embedding_text text not null,
    embedding_vector jsonb,
    updated_at varchar(64) not null
);

create table if not exists user_profile (
    user_id varchar(64) primary key,
    nickname varchar(128) not null,
    city varchar(64) not null,
    default_budget_level varchar(32) not null,
    default_pace varchar(32) not null,
    default_transport varchar(32) not null,
    created_at varchar(64) not null,
    updated_at varchar(64) not null
);

create table if not exists user_preference_tag (
    user_id varchar(64) not null,
    tag_type varchar(64) not null,
    tag_value varchar(128) not null,
    weight double precision not null,
    source varchar(64) not null,
    updated_at varchar(64) not null,
    primary key (user_id, tag_type, tag_value, source)
);

create table if not exists user_behavior_event (
    event_id varchar(64) primary key,
    user_id varchar(64) not null,
    event_type varchar(64) not null,
    poi_id varchar(64),
    route_id varchar(64),
    tag_snapshot jsonb not null,
    event_time varchar(64) not null
);

create table if not exists route_template (
    template_id varchar(64) primary key,
    scene varchar(64) not null,
    time_period varchar(32) not null,
    min_duration_minutes integer not null,
    max_duration_minutes integer not null,
    budget_level varchar(32) not null,
    pace_level varchar(32) not null,
    slot_sequence jsonb not null,
    suitable_districts jsonb not null
);

create table if not exists slot_transition_rule (
    from_slot varchar(64) not null,
    to_slot varchar(64) not null,
    weight double precision not null,
    reason text,
    primary key (from_slot, to_slot)
);

create table if not exists demo_user_case (
    case_id varchar(64) primary key,
    user_id varchar(64) not null,
    business_area_id varchar(64),
    user_query text not null,
    city varchar(64) not null,
    district varchar(64) not null,
    business_area varchar(128) not null,
    time_window varchar(32) not null,
    party_size integer not null,
    budget integer not null,
    prefer_tags jsonb not null,
    avoid_tags jsonb not null,
    expected_scene varchar(64) not null
);
